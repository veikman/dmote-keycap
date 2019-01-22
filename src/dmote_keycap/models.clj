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
            :z 1.44}
          :main
           {:x 13.35
            :y 12.95
            :z 6}}})  ; Understated.

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

(defn- smallflat-outer-body
  [footprint]
  (let [xy (map #(+ % wall-thickness) footprint)]
    (model/hull
      (model/translate [0 0 1]
        (model/extrude-linear
          {:height (get-in matias [:body :top :z]), :center false, :scale scale}
          (rounded xy 2)))
      (model/extrude-linear
        {:height 1, :center false}
        (rounded xy 2)))))

(defn- smallflat-inner-body
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
        (smallflat-outer-body footprint)
        (smallflat-inner-body footprint)))))

(defn- mediumflat-outer-body
  [footprint]
  (let [xy (map #(+ % wall-thickness) footprint)]
    (model/hull
      (model/extrude-linear
        {:height 1, :center false}
        (rounded (map #(* scale %) xy) 2))
      (model/translate [0 0 (- (get-in matias [:body :top :z]))]
        (model/extrude-linear
          {:height 1}
          (rounded xy 2)))
      (model/translate [0 0 (inc (- (get-in matias [:body :main :z])))]
        (model/extrude-linear
          {:height 1, :center false}
          (rounded xy 2))))))

(defn- mediumflat-inner-body
  [footprint]
  (let [xy (map compensator-general footprint)]
    (model/hull
      (model/translate [0 0 (- 0.5)]
        (model/extrude-linear
          {:height 0.5, :center false}
          (rounded (map #(* scale %) xy) 0.2)))
      (model/translate [0 0 (- (get-in matias [:body :main :z]))]
        (model/extrude-linear
          {:height (- (get-in matias [:body :main :z])
                      (get-in matias [:body :top :z]))
           :center false}
          (rounded xy 0.6))))))

(defn- mediumflat-composite-body
  []
  (let [footprint [(get-in matias [:body :main :x])
                   (get-in matias [:body :main :y])]]
    (model/difference
      (mediumflat-outer-body footprint)
      (mediumflat-inner-body footprint))))

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

(defn mediumflat-model
  "A flat keycap that covers a Matias switch tightly."
  [options]
  (model/union
    (mediumflat-composite-body)
    (stem options)))
