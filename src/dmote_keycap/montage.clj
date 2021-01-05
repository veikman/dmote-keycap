;;; 2D images for ease of inspecting lots of legends.

;;; This relies on imagemagick CLI utilities (convert, montage).

(ns dmote-keycap.montage
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]]
            [scad-app.misc :refer [compose-filepath]]))

(def dir-tmp (io/file "output" "png" "intermediate"))
(def dir-montage (io/file "output" "png" "montage"))
(def res-side 200)  ; Pixel width & height of OpenSCAD rendering.
(def res-asset 300)  ; Pixel width & height of intermediate (asset) montage.

(defn- track-image-filepath
  "Compose a filepath with tracking for images.
  Store the final path in passed “tracker” atom mapping."
  [tracker head suffix {:keys [tail] :as options}]
  (let [path (compose-filepath head suffix options)]
    (when (= suffix "png") (swap! tracker assoc-in [head (first tail)] path))
    path))

(defn- specify-image
  [key eye]
  {:name (name key), :camera {:eye eye, :center [0 0 0]},
   :size [res-side, res-side]})

(defn expand-asset
  "Finish an asset before handing it to scad-app.
  Conditionally add requests for 2D images and ensure they are tracked."
  [{:keys [montage]} tracker asset]
  (cond-> asset
    true (assoc :filepath-fn (partial track-image-filepath tracker))
    montage (assoc :images
                   (concat
                     [(specify-image :top   [0 0 40])
                      (specify-image :north [0 50 0])
                      (specify-image :east  [50 0 0])
                      (specify-image :south [0 -50 0])
                      (specify-image :west  [-50 0 0])]))))

(defn- asset-path [name] (str (io/file dir-montage (str name ".png"))))
(defn- asset-element [name] ["-label" name (asset-path name)])

(defn- asset-sequence
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
     ["montage" (tmp :north) (tmp :top) (tmp :south) "-geometry" "+0+0"
      "-tile" "1x" (tmp :axis)]
     ["montage" (tmp :west) (tmp :axis) (tmp :east) "-geometry" "1x1+0+0<"
      "-tile" "x1" (asset-path asset-name)]]))

(defn- imagemagick
  [cmd]
  (when-not (zero? (:exit (apply sh cmd)))
    (println "Montage failed: Unable to run" cmd)
    (System/exit 66)))

(defn montage!
  "Compose a 2D montage of images.
  This requires assets to have been transcoded to OpenSCAD and each image of
  them to have been specified on the passed tracker, a map of asset names to
  maps of side kes to Java file objects, all in an atom."
  [options tracker]
  (.mkdir dir-tmp)
  (.mkdir dir-montage)
  (doseq [old (.listFiles dir-montage)]
    (io/delete-file old))
  (doseq [[asset-name sides] @tracker]
    (doseq [cmd (asset-sequence asset-name sides)]
      (imagemagick cmd)))
  (imagemagick (concat ["montage" "-geometry"
                        (str res-asset "x" res-asset "+0+0")]
                       (mapcat asset-element (sort (keys @tracker)))
                       [(str (io/file dir-montage "montage.png"))])))

