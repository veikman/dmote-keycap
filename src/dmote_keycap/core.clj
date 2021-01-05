;;; A CLI application for generating 3D models.

(ns dmote-keycap.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [clojure.spec.alpha :as spec]
            [clojure.string :refer [join split]]
            [clojure.tools.cli :refer [parse-opts]]
            [environ.core :refer [env]]
            [scad-app.core :as app-core]
            [scad-app.misc :refer [compose-filepath]]
            [dmote-keycap.schema :as schema]
            [dmote-keycap.data :as data]
            [dmote-keycap.models :as models]
            [dmote-keycap.batch :refer [batch-assets]])
  (:gen-class :main true))

(def compass [:north :east :south :west])
(def dir-tmp (io/file "output" "png" "intermediate"))
(def dir-montage (io/file "output" "png" "montage"))

(defn stderr
  "Print to STDERR where applicable."
  [message]
  (binding [*out* *err*]
    (println message)))

(defn- nilable-number [raw] (when-not (= raw "nil") (Float/parseFloat raw)))

(def static-cli-options
  "Define command-line interface flags."
  [["-V" "--version" "Print program version and exit"]
   ["-h" "--help" "Print this message and exit"]
   ["-r" "--render" "Render SCAD to STL"]
   [nil "--rendering-program PATH" "Path to OpenSCAD" :default "openscad"]
   ["-m" "--montage" "When rendering to STL, also produce a 2D montage"]
   [nil "--batch PATH" "Input filepath for batch mode"]
   ["-w" "--whitelist RE"
    "Limit batch output to files whose names match the regular expression RE"
    :default #"" :parse-fn re-pattern]
   [nil "--filename NAME" "Output filename; no suffix"
    :default (:filename data/option-defaults)]
   [nil "--supported" "Include print supports underneath models"]
   [nil "--sectioned" "Show models in section (cut in half)"]
   [nil "--face-size N" "Smaller number gives more detail"
    :default 0.1, :parse-fn #(Float/parseFloat %),
    :validate [(partial spec/valid? ::app-core/minimum-face-size)]]
   [nil "--switch-type TYPE" "One of “alps” or “mx”"
    :default-desc "alps", :parse-fn keyword,
    :validate [(partial spec/valid? ::schema/switch-type)]]
   [nil "--style TYPE" "Main body; “minimal” or “maquette”"
    :default-desc "minimal", :parse-fn keyword,
    :validate [(partial spec/valid? ::data/style)]]
   [nil "--top-size 'X Y Z'" "Size of keycap finger plate in mm"
    :parse-fn (fn [raw] (mapv nilable-number (split raw #"\s+")))
    :validate [(partial spec/valid? ::data/top-size)]]
   [nil "--bowl-radii 'X Y Z'" "Radii of a spheroid that rounds out the top"
    :parse-fn (fn [raw] (mapv nilable-number (split raw #"\s+")))
    :validate [(partial spec/valid? ::data/bowl-radii)]]
   [nil "--skirt-length N" "Height of keycap up to top of switch stem"
    :parse-fn #(Float/parseFloat %)]
   [nil "--skirt-thickness N" "Thickness of walls descending around switch"
    :default (:skirt-thickness data/option-defaults)
    :parse-fn #(Float/parseFloat %)]
   [nil "--nozzle-width N" "FDM printer nozzle (aperture) width in mm"
    :default (:nozzle-width data/option-defaults)
    :parse-fn #(Float/parseFloat %)]
   [nil "--error-body-positive N" "Printer error in mm"
    :default (:error-body-positive data/option-defaults)
    :parse-fn #(Float/parseFloat %)]
   [nil "--error-stem-positive N" "Printer error in mm"
    :default (:error-stem-positive data/option-defaults)
    :parse-fn #(Float/parseFloat %)]
   [nil "--error-stem-negative N" "Printer error in mm"
    :default (:error-stem-negative data/option-defaults)
    :parse-fn #(Float/parseFloat %)]])

(def legend-cli-options
  (apply concat
    (for [face (concat [:top] compass)]
      (let [string (name face)
            as #(fn [coll _ val] (assoc-in coll [:legend :faces face %] val))]
        [[:long-opt (format "--legend-%s-unimportable" string)
          :required "PATH"
          :desc (format "Complex SVG file for the %s face" string)
          :assoc-fn (as :unimportable)]
         [:long-opt (format "--legend-%s-importable" string)
          :required "PATH"
          :desc (format "Simple 2D file for the %s face" string)
          :assoc-fn (as :importable)]
         [:long-opt (format "--legend-%s-char" string)
          :required "CHAR"
          :desc (format "Text to render on the %s face" string)
          :assoc-fn (as :char)]]))))

(defn- read-edn
  [{:keys [batch]}]
  (try
    (edn/read-string (slurp batch))
    (catch java.io.IOException e
      (stderr (format "File not available: “%s”." (.getMessage e))))
    (catch java.lang.RuntimeException _
      (stderr (format "File not valid EDN: “%s”." batch)))))

(defn- track-image-filepath
  "Compose a filepath with tracking for images.
  Store the final path in passed “tracker” atom mapping."
  [tracker head suffix {:keys [tail] :as options}]
  (let [path (compose-filepath head suffix options)]
    (when (= suffix "png") (swap! tracker assoc-in [head (first tail)] path))
    path))

(defn- specify-image
  [key eye size]
  {:name (name key)
   :camera {:eye eye, :center [0 0 0]}
   :size [size, size]})

(defn- finalize-asset
  "Finish an asset before handing it to scad-app.
  Conditionally add requests for 2D images and ensure they are tracked."
  [{:keys [montage]} tracker asset]
  (cond-> asset
    true (assoc :filepath-fn (partial track-image-filepath tracker))
    montage (assoc :images
                   (concat
                     [(specify-image :top   [0 0 40]  250)
                      (specify-image :north [0 50 0]  250)
                      (specify-image :east  [50 0 0]  250)
                      (specify-image :south [0 -50 0] 250)
                      (specify-image :west  [-50 0 0] 250)]))))

(defn- asset-montage-path
  [asset-name]
  (str (io/file dir-montage (str asset-name ".png"))))

(defn- partial-montage-seq
  "Prepare intermediate resources, comprising one asset, for a 2D montage.
  First, make the default OpenSCAD background colour transparent.
  Then, arrange the sides of the key around the image of its top, through
  a series of rotations and a pair of carefully centred montages starting with
  the north-south axis to establish the height of the composite image."
  [asset-name sides]
  (let [input (fn [k] (->> k name (get sides) str))
        tmp (fn [k] (str (io/file dir-tmp (str (name k) ".png"))))
        alpha (fn [k] ["-transparent" "#ffffe5" (input k) (tmp k)])]
    [(concat ["convert" "-rotate" "90"] (alpha :west))
     (concat ["convert" "-rotate" "270"] (alpha :east))
     (concat ["convert" "-rotate" "180"] (alpha :north))
     (concat ["convert"] (alpha :south))
     (concat ["convert"] (alpha :top))
     ["montage" (tmp :north) (tmp :top) (tmp :south) "-geometry" "+0+0" "-tile" "1x" (tmp :axis)]
     ["montage" (tmp :west) (tmp :axis) (tmp :east) "-geometry" "1x1+0+0<" "-tile" "x1"
      (asset-montage-path asset-name)]]))

(defn- final-montage-element
  [asset-name]
  ["-label" asset-name (asset-montage-path asset-name)])

(defn- imagemagick
  [cmd]
  (when-not (zero? (:exit (apply sh cmd)))
    (println "Montage failed: Unable to run" cmd)
    (System/exit 66)))

(defn- montage!
  "Compose a 2D montage of images specified in each asset rendered previously."
  [options tracker]
  (.mkdir dir-tmp)
  (.mkdir dir-montage)
  (doseq [old (.listFiles dir-montage)]
    (io/delete-file old))
  (doseq [[asset-name sides] @tracker]
    (doseq [cmd (partial-montage-seq asset-name sides)]
      (imagemagick cmd)))
  (imagemagick (concat ["montage" "-geometry" "250x250+0+0"]
                       (mapcat final-montage-element (sort (keys @tracker)))
                       [(str (io/file dir-montage "montage.png"))])))

(defn- build-all!
  "Call scad-app to write to file."
  [assets {:keys [montage] :as options}]
  (let [tracker (atom {})
        all (map (partial finalize-asset options tracker) assets)]
    (app-core/build-all all options)
    (when montage (montage! options tracker))))

(defn- build!
  "Define one scad-app asset and write to file."
  [options]
  (build-all! [{:name (:filename options)
                :model-main (models/keycap options)
                :minimum-face-size (:face-size options)}]
              options))

(defn- batch!
  "Build scad-app assets from an EDN file."
  [{:keys [whitelist] :as options}]
  (if-let [data (read-edn options)]
    (if (spec/valid? ::schema/batch-file data)
      (build-all! (app-core/filter-by-name whitelist
                                           (batch-assets options data))
                  options)
      (do
        (binding [*out* *err*]
          (println "Invalid data structure in EDN file. Details follow.")
          (spec/explain ::schema/batch-file data)
          (flush))
        (System/exit 65)))
    (System/exit 64)))

(defn -main
  "Basic command-line interface logic."
  [& raw]
  (let [args (parse-opts raw (concat static-cli-options legend-cli-options)
                         :in-order true)
        help-text (fn [] (println "dmote-keycap options:")
                         (println (:summary args)))
        version (fn [] (println "dmote-keycap version"
                         (env :dmote-keycap-version)))]
   (cond
     (get-in args [:options :help]) (help-text)
     (get-in args [:options :version]) (version)
     (:errors args) (fn [] (run! stderr (:errors args)) (System/exit 1))
     (get-in args [:options :batch]) (batch! (:options args))
     :else (build! (:options args)))))
