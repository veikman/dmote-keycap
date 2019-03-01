;;; Geometry.

(ns dmote-keycap.models
  (:require [scad-clj.model :as model]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]))

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


;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(def compensator-general (error-fn))
(def compensator-positive (error-fn 0.5))

(defn- inset-corner
  [[x y] radius]
  (let [initial #(- % radius)]
    (->> (model/square (initial x) (initial y))
         (model/offset radius))))

(defn- rounded-square
  [{:keys [footprint radius xy-thickness] :or {radius 2, xy-thickness 1}}]
  (inset-corner (map #(+ % xy-thickness) footprint) radius))

(defn- rounded-block
  [{:keys [z-offset z-thickness]
    :or {z-offset 0, z-thickness 1} :as dimensions}]
  (->> (rounded-square dimensions)
       (model/extrude-linear {:height z-thickness, :center false})
       (maybe/translate [0 0 z-offset])))

(defn- shell
  [sequence]
  [(map rounded-block sequence)
   (map #(rounded-block (assoc % :xy-thickness 0)) sequence)])

(defn- non-standard-body
  [{:keys [style plate-dimensions bowl-dimensions bowl-offset]
    :or {plate-dimensions [12.5 (dec (get-in matias [:body :top :y])) 2]
         bowl-dimensions [50 30 15]
         bowl-offset -1}}]
  (let [[plate-x plate-y plate-z] plate-dimensions
        top-footprint
          (map compensator-general
            [(get-in matias [:body :top :x])
             (get-in matias [:body :top :y])])
        main-footprint
          (map compensator-general
            [(get-in matias [:body :main :x])
             (get-in matias [:body :main :y])])
        shell-base
          [{:footprint top-footprint}
           {:footprint main-footprint
            :z-offset (- (get-in matias [:body :top :z]))}
           (when (= style :medium)
             {:footprint main-footprint
              :z-offset (inc (- (get-in matias [:body :main :z])))})]
        [positive negative] (shell (remove nil? shell-base))
        positive
          (cons positive
            (rounded-block {:z-thickness plate-z
                            :footprint [plate-x plate-y]}))]
    (model/difference
      (util/loft positive)
      (when bowl-dimensions
        (model/translate [0 0 (+ plate-z (/ (nth bowl-dimensions 2) 2) bowl-offset)]
          (model/resize bowl-dimensions
            (model/sphere 1000))))
      (model/intersection  ; Make sure the inner negative cuts off at z = 0.
        (util/loft negative)
        (model/translate [0 0 -100]
          (model/cube 200 200 200))))))

(defn- stem
  [options]
  (let [z (get-in matias [:stem :z :interior])]
    (model/translate [0 0 (- z)]
      (model/extrude-linear
        {:height z, :center false}
        (inset-corner [(compensator-positive (get-in matias [:stem :x]))
                       (compensator-positive (get-in matias [:stem :y]))]
                      0.2)))))

(defn- keycap
  [{:keys [sectioned] :as options}]
  (maybe/intersection
    (model/union
      (non-standard-body options)
      (stem options))
    (when sectioned
      (model/translate [100 0 0]
        (model/cube 200 200 200)))))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn smallflat-model
  "A flat keycap that barely covers the uppermost part of a Matias switch."
  [options]
  (keycap (assoc options :style :small)))

(defn mediumflat-model
  "A flat keycap that covers a Matias switch tightly."
  [options]
  (keycap (assoc options :style :medium)))
