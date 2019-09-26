;;; A CLI application for generating 3D models.

(ns dmote-keycap.core
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :refer [split]]
            [clojure.tools.cli :refer [parse-opts]]
            [environ.core :refer [env]]
            [scad-app.core :refer [build-all] :as app-core]
            [dmote-keycap.data :as data]
            [dmote-keycap.models :as models])
  (:gen-class :main true))

(defn- nilable-number [raw] (when-not (= raw "nil") (Float/parseFloat raw)))

(def cli-options
  "Define command-line interface flags."
  [["-V" "--version" "Print program version and exit"]
   ["-h" "--help" "Print this message and exit"]
   ["-r" "--render" "Render SCAD to STL"]
   [nil "--rendering-program PATH" "Path to OpenSCAD" :default "openscad"]
   [nil "--supported" "Include print supports underneath models"]
   [nil "--sectioned" "Show models in section (cut in half)"]
   [nil "--face-size N" "Smaller number gives more detail"
    :default 0.1, :parse-fn #(Float/parseFloat %),
    :validate [(partial spec/valid? ::app-core/minimum-face-size)]]
   [nil "--switch-type TYPE" "One of “alps” (default) or “mx”"
    :parse-fn keyword, :validate [(partial spec/valid? ::data/switch-type)]]
   [nil "--style TYPE"
    "Main body style; one of “minimal” (default) or “maquette”"
    :parse-fn keyword, :validate [(partial spec/valid? ::data/style)]]
   [nil "--top-size 'X Y Z'" "Size of keycap finger plate in mm"
    :parse-fn (fn [raw] (mapv nilable-number (split raw #"\s+")))
    :validate [(partial spec/valid? ::data/top-size)]]
   [nil "--skirt-length N" "Height of keycap up to top of switch stem"
    :parse-fn #(Float/parseFloat %)]
   [nil "--skirt-thickness N" "Thickness of walls descending around switch"
    :default (:skirt-thickness data/option-defaults)
    :parse-fn #(Float/parseFloat %)]
   [nil "--nozzle-width N" "Printer nozzle (aperture) width in mm"
    :parse-fn #(Float/parseFloat %)]
   [nil "--error-stem-positive N" "Printer error in mm"
    :parse-fn #(Float/parseFloat %)]
   [nil "--error-stem-negative N" "Printer error in mm"
    :parse-fn #(Float/parseFloat %)]])

(defn -main
  "Basic command-line interface logic."
  [& raw]
  (let [args (parse-opts raw cli-options)
        help-text (fn [] (println "dmote-keycap options:")
                         (println (:summary args)))
        version (fn [] (println "dmote-keycap version"
                         (env :dmote-keycap-version)))
        error (fn [] (run! println (:errors args)) (System/exit 1))]
   (cond
     (get-in args [:options :help]) (help-text)
     (get-in args [:options :version]) (version)
     (:errors args) (error)
     :else
       (let [options (:options args)]
         (build-all [{:name "cap",
                      :model-main (models/keycap options)
                      :minimum-face-size (:face-size options)}]
                    options)))))
