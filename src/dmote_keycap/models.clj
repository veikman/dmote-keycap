;;; Geometry.

(ns dmote-keycap.models
  (:require [scad-clj.model :as model]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]))

;;;;;;;;;;;;;;;
;; Constants ;;
;;;;;;;;;;;;;;;

;; Switch data here is based on real-world observation, not purely on data
;; sheets. Some components are modelled for printability and may be
;; incompatible with some versions of real switches.
(def switch-data
  {:alps
    {:travel 3.5
     :stem {:core           {:size {:x 4.5,   :y 2.2,   :z 5}
                             :positive true}}
     :body {:top            {:size {:x 11.4,  :y 10.2,  :z 7.3}}
            :core           {:size {:x 12.35, :y 11.34, :z 5.75}}
            :slider-housing {:size {:x 13.35, :y 5.95,  :z 5.15}}
            :snap           {:size {:x 12,    :y 13.03, :z 4.75}}}}
   :mx
    {:travel 3.6
     :stem {:shell          {:size {:x 7.5,   :y 5.6,   :z 3.6}
                             :positive true}
            :core           {:size {:x 5,     :y 5.8,   :z 3.6}
                             :positive true}
            :cross-x        {:size {:x 4,     :y 1.25,  :z 3.6}
                             :positive false}
            :cross-y        {:size {:x 1.1,   :y 4,     :z 3.6}
                             :positive false}}
     :body {:top            {:size {:x 10.2,  :y 11,    :z 6.6}}
            :core           {:size {:x 14.7,  :y 14.7,  :z 1}}
            :base           {:size {:x 15.6,  :y 15.6,  :z 0.7}}}}})


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
  [{:keys [footprint radius xy-thickness] :or {radius 1.8, xy-thickness 2.1}}]
  (inset-corner (map #(+ % xy-thickness) footprint) radius))

(defn- rounded-block
  [{:keys [z-offset z-thickness]
    :or {z-offset 0, z-thickness 0.01} :as dimensions}]
  (->> (rounded-square dimensions)
       (model/extrude-linear {:height z-thickness, :center false})
       (maybe/translate [0 0 z-offset])))

(defn- switch-body-cube
  [switch-type part-name]
  (let [{:keys [x y z]} (get-in switch-data [switch-type :body part-name :size])]
    (model/translate [0 0 (- (/ z 2) (switch-height switch-type))]
      (model/cube (compensator-general x) (compensator-general y) z))))

(defn- stem-body-cube
  "Overly similar to switch-body-cube but for stems."
  [part-properties]
  {:pre [(map? part-properties)
         (:size part-properties)]}
  (let [{:keys [x y z]} (:size part-properties)
        compensator (if (:positive part-properties)
                      compensator-positive compensator-general)
        ;; Use of compensators is currently overridden here because they are
        ;; inconsistently effective at small scales.
        compensator identity]
    (model/translate [0 0 (/ z -2)]
      (model/cube (compensator x) (compensator y) z))))

(defn- switch-body
  "Minimal interior space for a switch, starting at z = 0.
  This model consists of a named core part of the switch with all other parts
  radiating out from it."
  [switch-type]
  (util/radiate
    (switch-body-cube switch-type :core)
    (reduce
      (fn [coll part] (conj coll (switch-body-cube switch-type part)))
      []
      (remove (partial = :core) (switch-parts switch-type)))))

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
    :or {plate-dimensions [10 10 2.5]
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

(defn- stem-builder
  [switch-type pred]
  (let [data (get-in switch-data [switch-type :stem])]
    (map #(stem-body-cube (get data %))
         (filter (partial pred data) (keys data)))))

(defn- stem-model
  [{:keys [switch-type]}]
  (maybe/difference
    (apply maybe/union
      (stem-builder switch-type #(get-in %1 [%2 :positive])))
    (apply maybe/union
      (stem-builder switch-type #(not (get-in %1 [%2 :positive]))))))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn keycap
  [{:keys [sectioned] :as options}]
  (let [options (merge {:switch-type :alps} options)]
    (maybe/intersection
      (model/union
        (non-standard-body options)  ; TODO: Standard bodies like DSA.
        (stem-model options))
      (when sectioned
        (model/translate [100 0 0]
          (model/cube 200 200 200))))))
