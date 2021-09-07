;;; Jigs for sanding keys.

(ns dmote-keycap.sand
  (:require [scad-clj.model :as model]
            [scad-tarmi.core :refer [π sin]]
            [scad-tarmi.util :refer [loft]]
            [dmote-keycap.data :refer [style-defaults]]))

(def base-height 2.5)
(def screw-hole-offset 14)

(defn- plinth
  [{:keys [jig-angle paper-width bowl-diameters]}]
  (let [y-bowl (first bowl-diameters)
        y-base (+ y-bowl 3)
        r-base (* y-bowl (sin jig-angle))
        cylinder (model/cylinder r-base paper-width)]
    (model/rotate [jig-angle 0 0]
      (model/rotate [0 (/ π 2) 0]
        (model/hull
          (model/translate [0 (/ y-base 2) 0] cylinder)
          (model/translate [0 (/ y-base -2) 0] cylinder))))))

(defn- lane
  "A medallion shaped like the negative of a keycap’s top bowl, on a plinth."
  [{:keys [jig-angle paper-width bowl-diameters] :as options}]
  (let [[y-bowl x-bowl z-bowl] bowl-diameters
        bm (apply max bowl-diameters)
        y-base (+ y-bowl 3)
        r-base (* y-bowl (sin jig-angle))]
    (model/difference
      (model/union
        (model/rotate [jig-angle 0 0]
          (model/translate [0 0 r-base]
            (model/hull
              (model/extrude-linear {:height 0.1} (model/resize [paper-width y-base] (model/circle bm)))
              (model/translate [0 0 0.3] (model/resize [x-bowl y-bowl z-bowl] (model/sphere bm))))))
        (plinth options))
      (model/translate [0 0 -100] (model/cube 200 200 200)))))

(defn- base-edge
  [pitch]
  (model/hull
    (model/translate [(/ pitch -2) screw-hole-offset] (model/circle 6))
    (model/translate [(/ pitch -2) (- screw-hole-offset)] (model/circle 6))))

(defn jig
  "An array of lanes on a shared base, to be mounted on a board.
  Sandpaper of different grit is wrapped around each lane."
  [{:keys [jig-lanes paper-width bowl-radii] :as explicit-options}]
  (let [radii (or bowl-radii (get-in style-defaults [:minimal :bowl-radii]))
        diameters (mapv #(* 2 %) radii)
        options (assoc explicit-options :bowl-radii radii
                                        :bowl-diameters diameters)
        pitch (+ paper-width 8)]
    (model/difference
      (model/union
        (apply model/union
          (for [n (range jig-lanes)]
            (model/translate [(* n pitch) 0 0] (lane options))))
        (model/translate [0 0 (- base-height)]
          (model/extrude-linear {:height base-height, :center false}
            (model/hull
              (model/cut
                (model/hull
                  (plinth options)
                  (model/translate [(* (dec jig-lanes) pitch) 0 0] (plinth options))))
              (base-edge pitch)
              (model/translate [(* jig-lanes pitch) 0] (base-edge pitch))))))
      (apply model/union
        (for [x (range (inc jig-lanes))
              y [screw-hole-offset (- screw-hole-offset)]]
          (model/translate [(* (- x 0.5) pitch) y 0]
            (loft [(model/translate [0 0 (- base-height)] (model/cylinder 1 1))
                   (model/translate [0 0 (/ base-height -2)] (model/cylinder 2 1))
                   (model/cylinder 3.5 1)])))))))
