;;; Jigs for sanding keys.

(ns dmote-keycap.sand
  (:require [scad-clj.model :as model]
            [scad-tarmi.core :refer [√ π sin cos]]
            [scad-tarmi.util :refer [loft]]
            [scad-tarmi.maybe :as maybe]
            [dmote-keycap.data :refer [style-defaults]]))

(def base-height 2.5)

(defn- chord
  "The chord length of a circle with x as its sagitta."
  [x r]
  (√ (* (- r (/ x 2)) (* 8 x))))

(defn- medallion-chords
  "Compute the chords of each dimension of a medallion.
  This assumes, incorrectly, the shape of a circle in each dimension.
  It gives increasingly misleading results for heterogenous bowl-radii."
  [{:keys [bowl-radii medallion-height]}]
  (map (partial chord medallion-height) bowl-radii))

(defn- plinth
  "The basic shape of a sanding block."
  [{:keys [jig-angle paper-width bowl-radii to-ground face-shape] :as options}]
  (let [y-bowl (* 2 (first bowl-radii))  ; Can’t use chords here.
        y-base (+ y-bowl 3)
        r-base (* y-bowl (sin jig-angle))
        cylinder (model/cylinder r-base paper-width)]
    (maybe/union
      (maybe/hull
        (model/rotate [jig-angle 0 0]
          (maybe/union
            (model/rotate [0 (/ π 2) 0]
              (model/hull
                (model/translate [0 (/ y-base 2) 0] cylinder)
                (model/translate [0 (/ y-base -2) 0] cylinder)))))
        (when to-ground
          (model/translate [0 0 (- base-height)]
            (model/extrude-linear {:height 1, :center false}
              (model/cut (plinth (dissoc options :to-ground :face-shape)))))))
      (when face-shape
        (model/rotate [jig-angle 0 0]
          (model/translate [0 0 r-base]
            face-shape))))))

(defn- lane
  "A medallion shaped like the negative of a keycap’s top bowl, on a plinth."
  [{:keys [paper-width bowl-radii medallion-height] :as options}]
  (let [[_ __ rz] bowl-radii
        [y-bowl x-bowl z-bowl] (mapv #(* 2 %) bowl-radii)]
    (model/difference
      (plinth
        (assoc options
          :to-ground true
          :face-shape
          ;; The medallion.
          (model/intersection
            (model/translate [0 0 (min 0 (- medallion-height rz))]
              (model/resize [x-bowl y-bowl z-bowl]
                (model/sphere
                  (apply max (medallion-chords options)))))
            ;; Block off any part of the sphere below the face of the plinth.
            (model/translate [0 0 50]
              (model/cube paper-width y-bowl 100)))))
      ;; Cut away below the base at the back.
      (model/translate [0 0 (- (- base-height) 100)] (model/cube 200 200 200))
      ;; Cut away to the upper edge of the base at the front.
      (model/translate [0 -100 -100] (model/cube 200 200 200)))))

(defn- base-edge
  [pitch screw-offset]
  (model/hull
    (model/translate [(/ pitch -2) screw-offset] (model/circle 6))
    (model/translate [(/ pitch -2) (- screw-offset)] (model/circle 6))))

(defn jig
  "An array of lanes on a shared base, to be mounted on a scrap board.
  Sandpaper of different grit is wrapped around each lane and a printed keycap
  is rubbed, top down, against the medallion at the centre of each lane. The
  shape of the medallion perfectly matches the bowl of the keycap, being based
  on the same setting, “bowl-radii”."
  [{:keys [jig-lanes jig-angle paper-width bowl-radii] :as explicit-options}]
  (let [radii (or bowl-radii (get-in style-defaults [:minimal :bowl-radii]))
        options (assoc explicit-options
                       :bowl-radii radii
                       :medallion-height (min (last radii) 3.5))
        pitch (+ paper-width 8)
        screw-offset (/ (* (apply max (medallion-chords options)) (cos jig-angle)) 2)]
    (model/difference
      (model/union
        ;; The array of individual lanes.
        (apply model/union
          (for [n (range jig-lanes)]
            (model/translate [(* n pitch) 0 0] (lane options))))
        ;; The shared base.
        (model/translate [0 0 (- base-height)]
          (model/extrude-linear {:height base-height, :center false}
            (model/hull
              (model/cut
                (model/hull
                  (plinth options)
                  (model/translate [(* (dec jig-lanes) pitch) 0 0]
                    (plinth options))))
              ;; Extra material outside the array, for corner screws.
              (base-edge pitch screw-offset)
              (model/translate [(* jig-lanes pitch) 0]
                (base-edge pitch screw-offset))))))
      ;; Screw holes for attaching the jig to a scrap piece of board.
      ;; These go between and outside the lanes.
      (apply model/union
        (for [x (range (inc jig-lanes))
              y [screw-offset (- screw-offset)]]
          (model/translate [(* (- x 0.5) pitch) y 0]
            (loft [(model/translate [0 0 (- base-height)]
                     (model/cylinder 1.7 1))
                   (model/translate [0 0 -1.5]
                     (model/cylinder 2 (/ base-height 2)))
                   (model/cylinder 3.7 1)])))))))
