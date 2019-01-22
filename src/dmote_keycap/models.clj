;;; Geometry.

(ns dmote-keycap.models
  (:require [scad-clj.model :as model]
            [scad-tarmi.dfm :refer [error-fn]]))

;;;;;;;;;;;;;;;
;; Constants ;;
;;;;;;;;;;;;;;;

(def matias
  {:travel 3.5
   :stem {:x 4.5
          :y 2.2
          :z {:interior 5
              :exterior 4}}
   :body {:top
           {:x 13.31
            :y 11.27
            :z 1.44}}})

(def wall-thickness 1)
(def scale 0.9)

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(def compensator-general (error-fn))
(def compensator-positive (error-fn 0.5))

(defn- rounded
  [[x y] radius]
  (let [initial #(- % radius)]
    (->> (model/square (initial x) (initial y))
         (model/offset radius))))

(defn- outer-body
  [footprint]
  (let [xy (map #(+ % wall-thickness) footprint)]
    (model/hull
      (model/translate [0 0 1]
        (model/extrude-linear
          {:height (get-in matias [:body :top :z]) :center false :scale scale}
          (rounded xy 2)))
      (model/extrude-linear
        {:height 1 :center false}
        (rounded xy 2)))))

(defn- inner-body
  [footprint]
  (let [xy (map compensator-general footprint)]
    (model/extrude-linear
      {:height (get-in matias [:body :top :z]), :center false, :scale scale}
      (rounded xy 0.6))))

(defn- smallflat-composite-body
  []
  (let [footprint [(get-in matias [:body :top :x])
                   (get-in matias [:body :top :y])]]
    (model/translate [0 0 (- (get-in matias [:body :top :z]))]
      (model/difference
        (outer-body footprint)
        (inner-body footprint)))))

(defn- stem
  [options]
  (let [z (get-in matias [:stem :z :interior])]
    (model/translate [0 0 (- z)]
      (model/extrude-linear
        {:height z, :center false}
        (rounded [(compensator-positive (get-in matias [:stem :x]))
                  (compensator-positive (get-in matias [:stem :y]))]
                 0.4)))))

;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn smallflat-model
  "A flat keycap that barely covers the uppermost part of a Matias switch."
  [options]
  (model/union
    (smallflat-composite-body)
    (stem options)))
