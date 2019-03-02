;;; Geometry.

(ns dmote-keycap.models
  (:require [scad-clj.model :as model]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]))

;;;;;;;;;;;;;;;
;; Constants ;;
;;;;;;;;;;;;;;;

(def switch-data
  {:alps
    {:travel 3.5
     :stem {:x 4.5
            :y 2.2
            :z {:interior 5
                :exterior 4}}
     :body {:top  ; The flat upper surface of the switch body.
             {:size
               {:x 11.4
                :y 10.2
                :z 7.3}}
            :core
             {:size
               {:x 12.35
                :y 11.34
                :z 5.75}}
            :rail-housing
             {:size
               {:x 13.35
                :y 5.95
                :z 5.15}}
            :snap
             {:size
               {:x 12
                :y 13.03
                :z 4.75}}}}})


;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(def compensator-general (error-fn))
(def compensator-positive (error-fn 0.25))

(defn- switch-parts
  "The keyword names of the parts of a switch body."
  [switch-type]
  (keys (get-in switch-data [switch-type :body])))

(defn- switch-dimension
  "The total height of a switch’s body over the mounting plate."
  [switch-type dimension]
  {:pre [(get switch-data switch-type)]
   :post [(number? %)]}
  (apply max (map #(get-in switch-data [switch-type :body % :size dimension])
                  (switch-parts switch-type))))

(defn- switch-height
  "The total height of a switch’s body over the mounting plate."
  [switch-type]
  (switch-dimension switch-type :z))

(defn- switch-footprint
  "The footprint of a switch’s body on the mounting plate."
  [switch-type]
  [(switch-dimension switch-type :x)
   (switch-dimension switch-type :y)])

(defn- switch-level-section
  "Find a vector of vertical sections of a switch."
  [old switch-type min-z]
  {:pre [(get switch-data switch-type)
         (number? min-z)]}
  (reduce
    (fn [coll part]
      (let [{:keys [x y z]} (get-in switch-data [switch-type :body part :size])]
        (if (<= min-z z) (conj coll [x y]) coll)))
    (or old [])
    (switch-parts switch-type)))

(defn- switch-sections
  "A map of xy slices through a composited switch body."
  [switch-type]
  (reduce
    (fn [coll part]
      (let [z (get-in switch-data [switch-type :body part :size :z])]
        (update coll z switch-level-section switch-type z)))
    {0 [(switch-footprint switch-type)]}
    (switch-parts switch-type)))

(defn- rectangular-sections
  "Simplified switch sections for a squarish outer body of a keycap."
  [switch-type]
  (let [full (switch-sections switch-type)]
    (reduce
      (fn [coll z]
        (let [xy-pairs (get full z)]
          (assoc coll z
            [(apply max (map first xy-pairs))
             (apply max (map second xy-pairs))])))
      full
      (keys full))))

(defn- inset-corner
  [[x y] radius]
  (let [initial #(- % (* 2 radius))]
    (->> (model/square (initial x) (initial y))
         (model/offset radius))))

(defn- rounded-square
  [{:keys [footprint radius xy-thickness] :or {radius 2, xy-thickness 2}}]
  (inset-corner (map #(+ % xy-thickness) footprint) radius))

(defn- rounded-block
  [{:keys [z-offset z-thickness]
    :or {z-offset 0, z-thickness 1} :as dimensions}]
  (->> (rounded-square dimensions)
       (model/extrude-linear {:height z-thickness, :center false})
       (maybe/translate [0 0 z-offset])))

(defn- switch-body
  "Minimal interior space for a switch, starting at z = 0."
  [switch-type]
  (apply model/hull
    (reduce
      (fn [coll part]
        (let [{:keys [x y z]} (get-in switch-data [switch-type :body part :size])]
          (conj coll
            (model/translate [0 0 (- (/ z 2) (switch-height switch-type))]
              (model/cube (compensator-general x) (compensator-general y) z)))))
      []
      (switch-parts switch-type))))

(defn- shell
  "Two vectors of rounded blocks, positive and negative."
  [sequence]
  [(map rounded-block sequence)
   (map #(rounded-block (assoc % :xy-thickness 0)) sequence)])

(defn- tight-shell
  [switch-type]
  (let [rectangles-by-z (rectangular-sections switch-type)]
    (reduce
      (fn [coll z]
        (conj coll
          {:footprint (get rectangles-by-z z)
           :z-offset (- z (switch-height switch-type))}))
      []
      (reverse (sort (keys rectangles-by-z))))))

(defn- non-standard-body
  [{:keys [switch-type plate-dimensions
           bowl-dimensions bowl-offset max-skirt-length]
    :or {switch-type :alps
         plate-dimensions [10 10 2]
         bowl-dimensions [50 30 15]
         bowl-offset -1.5}}]
  (let [max-skirt-length (or max-skirt-length
                             (dec (switch-height switch-type)))
        [plate-x plate-y plate-z] plate-dimensions
        [positive negative] (shell (tight-shell switch-type))
        positive
          (cons positive
            (rounded-block {:z-thickness plate-z
                            :footprint [plate-x plate-y]}))]
    (model/difference
      (model/intersection
        (model/difference
          (util/loft positive)
          (when bowl-dimensions
            (model/translate [0 0 (+ plate-z (/ (nth bowl-dimensions 2) 2) bowl-offset)]
              (model/resize bowl-dimensions
                (model/sphere 1000))))))
      (switch-body switch-type)
      (model/intersection  ; Make sure the inner negative cuts off at z = 0.
        (util/loft negative)
        (model/translate [0 0 -100]
          (model/cube 200 200 200)))
      ;; Cut everything before hitting the mounting plate:
      (model/translate [0 0 (- -100 max-skirt-length)]
        (model/cube 200 200 200)))))

(defn- stem
  [options]
  (let [z (get-in switch-data [:alps :stem :z :interior])]
    (model/translate [0 0 (- z)]
      (model/extrude-linear
        {:height z, :center false}
        (inset-corner
          [(compensator-positive (get-in switch-data [:alps :stem :x]))
           (compensator-positive (get-in switch-data [:alps :stem :y]))]
          0.2)))))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn keycap
  [{:keys [sectioned] :as options}]
  (maybe/intersection
    (model/union
      (non-standard-body options)  ; TODO: Standard bodies like DSA.
      (stem options))
    (when sectioned
      (model/translate [100 0 0]
        (model/cube 200 200 200)))))
