;;; Geometry.

(ns dmote-keycap.models
  (:require [scad-clj.model :as model]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]
            [dmote-keycap.data :as data]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(defn- maquette-body
  "The shape of one keycap, greatly simplified.
  The simplification is so extensive that this keycap can only be used for
  previews in keyboard models. It is not hollow and therefore useless if
  printed.
  The default height and slope are based on a DSA profile. Passing a
  non-default ‘top-size’ and ‘top-rotation’ can provide a rough approximation
  of SA and OEM caps, etc."
  [{:keys [unit-size slope top-size top-rotation max-skirt-length]
    :or {slope 0.73, top-size [nil nil 1], top-rotation [0 0 0]}}]
  (let [top-thickness (nth top-size 2)
        top-plate
          (if (every? some? top-size)
            top-size
            (conj (mapv #(* slope (data/key-length %)) unit-size)
                  top-thickness))]
    (model/hull
      (maybe/translate [0 0 (/ top-thickness 2)]
        (maybe/rotate top-rotation
          (apply model/cube top-plate)))
      (maybe/translate [0 0 (- max-skirt-length)]
        (apply model/cube (conj (mapv data/key-length unit-size) 0.01))))))

(defn- switch-parts
  "The keyword names of the parts of a switch body."
  [switch-type]
  (keys (get-in data/switches [switch-type :body])))

(defn- switch-dimension
  "The total height of a switch’s body over the mounting plate."
  [switch-type dimension]
  {:pre [(get data/switches switch-type)]
   :post [(number? %)]}
  (apply max (map #(get-in data/switches [switch-type :body % :size dimension])
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
  {:pre [(get data/switches switch-type)
         (number? min-z)]}
  (reduce
    (fn [coll part]
      (let [{:keys [x y z]} (get-in data/switches [switch-type :body part :size])]
        (if (<= min-z z) (conj coll [x y]) coll)))
    (or old [])
    (switch-parts switch-type)))

(defn- switch-sections
  "A map of xy slices through a composited switch body."
  [switch-type]
  (reduce
    (fn [coll part]
      (let [z (get-in data/switches [switch-type :body part :size :z])]
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
    :or {z-offset 0, z-thickness 0.01}
    :as dimensions}]
  {:pre [(number? z-offset)]}
  (->> (rounded-square dimensions)
       (model/extrude-linear {:height z-thickness, :center false})
       (maybe/translate [0 0 z-offset])))

(defn- switch-body-cube
  [{:keys [switch-type error-body-positive] :or {error-body-positive -0.5}}
   part-name]
  (let [{:keys [x y z]} (get-in data/switches [switch-type :body part-name :size])
        compensator (error-fn error-body-positive)]
    (model/translate [0 0 (- (/ z 2) (switch-height switch-type))]
      (model/cube (compensator x) (compensator y) z))))

(defn- stem-body-cube
  "Overly similar to switch-body-cube but for stems.
  The stem body is extremely sensitive to printing inaccuracies.
  Generally, an ALPS-style stem will print OK without compensation for error
  on a Lulzbot TAZ6, whereas the negatives space inside an MX-style stem will
  be too tight without compensation and too loose with standard muzzle-width
  compensation."
  [{:keys [error-stem-positive error-stem-negative]
    :or {error-stem-positive 0, error-stem-negative 0}}
   part-properties]
  {:pre [(map? part-properties)
         (:size part-properties)]}
  (let [{:keys [x y z]} (:size part-properties)
        error (if (:positive part-properties) error-stem-positive
                                              error-stem-negative)
        compensator (error-fn error)]
    (model/translate [0 0 (/ z -2)]
      (model/cube (compensator x) (compensator y) z))))

(defn- switch-body
  "Minimal interior space for a switch, starting at z = 0.
  This model consists of a named core part of the switch with all other parts
  radiating out from it."
  [{:keys [switch-type] :as options}]
  (util/radiate
    (switch-body-cube options :core)
    (reduce
      (fn [coll part] (conj coll (switch-body-cube options part)))
      []
      (remove (partial = :core) (switch-parts switch-type)))))

(defn- rounded-frames
  "Two vectors of rounded blocks, positive and negative."
  [sequence]
  [(map rounded-block sequence)
   (map #(rounded-block (assoc % :xy-thickness 0)) sequence)])

(defn- tight-shell-sequence
  [switch-type]
  (let [rectangles-by-z (rectangular-sections switch-type)]
    (reduce
      (fn [coll z]
        (conj coll
          {:footprint (get rectangles-by-z z)
           :z-offset (- z (switch-height switch-type))}))
      []
      (reverse (sort (keys rectangles-by-z))))))

(defn- minimal-body
  "A minimal (tight) keycap body with a skirt descending from a top plate.
  The top plate rises at the edges to form a bowl. The ‘top-size’ argument
  describes the plate, including the final thickness of the plate at its
  center. The ‘bowl-radii’ argument describes the sphere used as a negative to
  carve out the bowl."
  [{:keys [switch-type top-size bowl-radii bowl-plate-offset max-skirt-length]
    :or {top-size [9 9 1], bowl-radii [15 10 2], bowl-plate-offset 0}
    :as options}]
  (let [[plate-x plate-y top-z] top-size
        bowl-rz (nth (or bowl-radii [0 0 0]) 2)
        bowl-diameters (map #(* 2 %) bowl-radii)
        plate-z (+ top-z bowl-rz)
        [positive negative] (rounded-frames (tight-shell-sequence switch-type))
        positive
          (cons positive
            (rounded-block {:z-thickness plate-z
                            :footprint [plate-x plate-y]}))]
    (model/difference
      (model/intersection
        (model/difference
          (util/loft positive)
          (when bowl-radii
            (model/translate [0 0 (+ top-z bowl-rz bowl-plate-offset)]
              (model/resize bowl-diameters
                (model/sphere 1000))))))
      (switch-body options)
      (model/intersection  ; Make sure the inner negative cuts off at z = 0.
        (util/loft negative)
        (model/translate [0 0 -100]
          (model/cube 200 200 200)))
      ;; Cut everything before hitting the mounting plate:
      (model/translate [0 0 (- -100 max-skirt-length)]
        (model/cube 200 200 200)))))

(defn- stem-builder
  [{:keys [switch-type] :as options} pred]
  (let [data (get-in data/switches [switch-type :stem])]
    (map #(stem-body-cube options (get data %))
         (filter (partial pred data) (keys data)))))

(defn- stem-model
  [options]
  (maybe/difference
    (apply maybe/union
      (stem-builder options #(get-in %1 [%2 :positive])))
    (apply maybe/union
      (stem-builder options #(not (get-in %1 [%2 :positive]))))))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn keycap
  "A model of one keycap. Resolution is left at OpenSCAD defaults throughout."
  [{:keys [switch-type body-style sectioned]
    :or {switch-type :alps, body-style :minimal}
    :as options}]
  {:pre [(contains? (set (keys data/switches)) switch-type)
         (contains? data/body-styles body-style)]}
  (let [options (merge {:switch-type switch-type
                        :unit-size [1 1]
                        :max-skirt-length (dec (switch-height switch-type))}
                       options)]
    (maybe/intersection
      (model/union
        (case body-style
          :maquette (maquette-body options)
          :minimal (minimal-body options))
        (when-not (= body-style :maquette)
          (stem-model options)))
      (when sectioned
        (model/translate [100 0 0]
          (model/cube 200 200 200))))))
