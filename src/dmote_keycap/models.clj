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
  [{:keys [footprint radius thickness] :or {radius 2, thickness 1}}]
  (inset-corner (map #(+ % thickness) footprint) radius))

(defn- hollow-3d
  [{:keys [z-offset] :or {z-offset 0} :as dimensions}]
  (->> (rounded-square dimensions)
       (model/extrude-linear {:height 1, :center false})
       (maybe/translate [0 0 z-offset])))

(defn- shell
  [sequence]
  [(map hollow-3d sequence)
   (map #(hollow-3d (assoc % :thickness 0)) sequence)])

(defn- non-standard-body
  [{:keys [style]}]
  (let [top-footprint
          (map compensator-general
            [(get-in matias [:body :top :x])
             (get-in matias [:body :top :y])])
        main-footprint
          (map compensator-general
            [(get-in matias [:body :main :x])
             (get-in matias [:body :main :y])])
        shell-base
          (case style
            :small [{:footprint top-footprint}
                    {:footprint main-footprint
                     :z-offset (- (get-in matias [:body :top :z]))}]
            :medium [{:footprint top-footprint}
                     {:footprint main-footprint
                      :z-offset (- (get-in matias [:body :top :z]))}
                     {:footprint main-footprint
                      :z-offset (inc (- (get-in matias [:body :main :z])))}])
        [shell-outer shell-inner] (shell shell-base)]
    (model/difference
      (util/loft shell-outer)
      (model/intersection
        (util/loft shell-inner)
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
