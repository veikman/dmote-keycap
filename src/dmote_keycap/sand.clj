;;; Jigs for sanding keys.

(ns dmote-keycap.sand
  (:require [scad-clj.model :as model]
            [scad-tarmi.core :refer [π sin]]
            [scad-tarmi.util :refer [loft]]))

(def width 22)
(def base-height 2.5)
(def bowl-pitch 30)
(def bowl-count 3)
(def screw-hole-offset 14)

(defn- base-for-bowl
  [{:keys [bowl-radii] :or {bowl-radii [30 20 4]}}]
  (let [[y-bowl x-bowl z-bowl] bowl-radii
        y-base (+ y-bowl 3)
        θ 0.2
        r-base (* y-bowl (sin θ))]
    (model/rotate [θ 0 0]
      (model/rotate [0 (/ π 2) 0]
        (model/hull
           (model/translate [0 (/ y-base 2) 0] (model/cylinder r-base width))
           (model/translate [0 (/ y-base -2) 0] (model/cylinder r-base width)))))))

(defn bowl
  "A shape like the negative of a minimal top bowl."
  [{:keys [bowl-radii] :or {bowl-radii [30 20 4]} :as options}]
  (let [[y-bowl x-bowl z-bowl] bowl-radii
        bm (apply max bowl-radii)
        y-base (+ y-bowl 3)
        θ 0.2
        r-base (* y-bowl (sin θ))]
    (model/difference
      (model/union
        (model/rotate [θ 0 0]
          (model/translate [0 0 r-base]
            (model/hull
              (model/extrude-linear {:height 0.1} (model/resize [width y-base] (model/circle bm)))
              (model/translate [0 0 0.3] (model/resize [x-bowl y-bowl z-bowl] (model/sphere bm))))))
        (base-for-bowl options))
      (model/translate [0 0 -100] (model/cube 200 200 200)))))

(defn- base-edge
  []
  (model/hull
    (model/translate [(/ bowl-pitch -2) screw-hole-offset] (model/circle 6))
    (model/translate [(/ bowl-pitch -2) (- screw-hole-offset)] (model/circle 6))))

(defn bowl-array
  [options]
  (model/difference
    (model/union
      (apply model/union
        (for [n (range bowl-count)]
          (model/translate [(* n bowl-pitch) 0 0] (bowl options))))
      (model/translate [0 0 (- base-height)]
        (model/extrude-linear {:height base-height, :center false}
          (model/hull
            (model/cut
              (model/hull
                (base-for-bowl options)
                (model/translate [(* (dec bowl-count) bowl-pitch) 0 0] (base-for-bowl options))))
            (base-edge)
            (model/translate [(* bowl-count bowl-pitch) 0] (base-edge))))))
    (apply model/union
      (for [x (range (inc bowl-count))
            y [screw-hole-offset (- screw-hole-offset)]]
        (model/translate [(* (- x 0.5) bowl-pitch) y 0]
          (loft [(model/translate [0 0 (- base-height)] (model/cylinder 1 1))
                 (model/translate [0 0 (/ base-height -2)] (model/cylinder 2 1))
                 (model/cylinder 3.5 1)]))))))
