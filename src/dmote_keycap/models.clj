;;; Geometry.

(ns dmote-keycap.models
  (:require [clojure.spec.alpha :as spec]
            [scad-clj.model :as model]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]
            [dmote-keycap.data :as data]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(def wafer 0.01)

(defn- positives [coll key] (get-in coll [key :positive]))
(defn- negatives [coll key] (not (get-in coll [key :positive])))
(defn- switch-data [switch-type key] (get-in data/switches [switch-type key]))
(defn- section-keys [data pred] (filter (partial pred data) (keys data)))

(defn- stem-length
  "The length of the longest positive piece of the stem on a keycap.
  This should be the full interior height of the slider on a switch."
  [switch-type]
  (let [data (switch-data switch-type :stem)]
    (apply max (map #(get-in data [% :size :z])
                    (section-keys data positives)))))

(defn- print-bed-level
  "The level of the print bed (i.e. the bottom of an upright keycap model)
  relative to the top of the stem."
  [{:keys [switch-type skirt-length]}]
  (- (max (stem-length switch-type) skirt-length)))

(defn- maquette-body
  "The shape of one keycap, greatly simplified.
  The simplification is so extensive that this keycap can only be used for
  previews in keyboard models. It is not hollow and therefore useless if
  printed.
  The default height and slope are based on a DSA profile. Passing a
  non-default ‘top-size’ and ‘top-rotation’ can provide a rough approximation
  of SA and OEM caps, etc."
  [{:keys [switch-type unit-size slope top-size top-rotation skirt-length]
    :or {top-size [nil nil 1], top-rotation [0 0 0]}}]
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
      (maybe/translate [0 0 (- skirt-length)]
        (apply model/cube (conj (mapv data/key-length unit-size) wafer))))))

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
    (data/switch-parts switch-type)))

(defn- switch-sections
  "A map of xy slices through a composited switch body."
  [switch-type]
  (reduce
    (fn [coll part]
      (let [z (get-in data/switches [switch-type :body part :size :z])]
        (update coll z switch-level-section switch-type z)))
    {0 [(data/switch-footprint switch-type)]}
    (data/switch-parts switch-type)))

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
  {:pre [(number? x) (number? y) (number? radius)]}
  (let [initial #(- % (* 2 radius))]
    (->> (model/square (initial x) (initial y))
         (model/offset radius))))

(defn- rounded-square
  [{:keys [footprint radius skirt-thickness] :or {radius 1.8}}]
  (inset-corner (map #(+ % skirt-thickness) footprint) radius))

(defn- rounded-block
  [{:keys [z-offset z-thickness]
    :or {z-offset 0, z-thickness wafer}
    :as dimensions}]
  {:pre [(number? z-offset)]}
  (->> (rounded-square dimensions)
       (model/extrude-linear {:height z-thickness, :center false})
       (maybe/translate [0 0 z-offset])))

(defn- switch-body-cube
  [{:keys [switch-type error-body-positive]}
   part-name]
  (let [{:keys [x y z]} (get-in data/switches [switch-type :body part-name :size])
        compensator (error-fn error-body-positive)]
    (model/translate [0 0 (- (/ z 2) (data/switch-height switch-type))]
      (model/cube (compensator x) (compensator y) z))))

(defn- stem-body-cuboid
  "Overly similar to switch-body-cube but for stems.
  The stem body is extremely sensitive to printing inaccuracies.
  Generally, an ALPS-style stem will print OK without compensation for error
  on a Lulzbot TAZ6, whereas the negative space inside an MX-style stem will
  be too tight without compensation and too loose with standard nozzle-width
  compensation."
  [{:keys [error-stem-positive error-stem-negative]}
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
      (remove (partial = :core) (data/switch-parts switch-type)))))

(defn- switch-top-sizes
  "A sequence of size descriptors for the topmost rectangle(s) on a switch."
  [switch-type]
  (let [data (switch-data switch-type :body)
        sizes (map #(get-in data [% :size]) (keys data))
        max-z (apply max (map :z sizes))]
    (filter #(= max-z (:z %)) sizes)))

(defn- switch-top-rectangles
  [switch-type error thickness]
  (let [compensator (error-fn error)
        rect #(model/cube (compensator (:x %)) (compensator (:y %)) thickness)]
    (map rect (switch-top-sizes switch-type))))

(defn- stem-footprint
  "The maximal rectangular footprint of the positive features of a stem."
  [switch-type error]
  (let [data (switch-data switch-type :stem)
        sizes (map #(get-in data [% :size]) (section-keys data positives))
        compensator (error-fn error)]
    (mapv #(compensator (apply max (map % sizes))) [:x :y])))

(defn- vaulted-ceiling
  "An interior triangular profile resembling a gabled roof. The purpose of this
  shape is to reduce the need for printed supports while also saving some
  material in the top plate of a tall minimal-style cap."
  [{:keys [switch-type top-size error-stem-positive error-body-positive]
    :as options}]
  (let [top-z (last top-size)
        peak-z (dec top-z)
        overshoot (* 2 peak-z)
        outer-profile
          (apply maybe/hull
            (switch-top-rectangles switch-type error-body-positive wafer))
        [stem-x stem-y] (stem-footprint switch-type error-stem-positive)
        inner-profile (model/cube stem-x stem-y wafer)]
    (when (pos? peak-z)
      (model/difference
        (model/hull
          (model/translate [0 0 overshoot] inner-profile)
          outer-profile)
        (model/hull
          (model/translate [0 0 overshoot] outer-profile)
          inner-profile)))))

(defn- rounded-frames
  "Two vectors of rounded blocks, positive and negative.
  The inner sequence uses a horizontal (skirt) thickness of zero."
  [sequence]
  [(map rounded-block sequence)
   (map #(rounded-block (assoc % :skirt-thickness 0)) sequence)])

(defn- tight-shell-sequence
  [{:keys [switch-type] :as options}]
  (let [rectangles-by-z (rectangular-sections switch-type)]
    (reduce
      (fn [coll z]
        (conj coll
          (merge options
                 {:footprint (get rectangles-by-z z)
                  :z-offset (- z (data/switch-height switch-type))})))
      []
      (reverse (sort (keys rectangles-by-z))))))

(defn- minimal-options
  "An option mapping enriched with derived values for the minimal style."
  [{:keys [switch-type top-size bowl-radii]
    :or {top-size [9 9 1], bowl-radii [15 10 2]}
    :as explicit}]
  (let [bowl-rz (nth (or bowl-radii [0 0 0]) 2)
        [plate-x plate-y top-z] top-size]
    (merge {:bowl-rz bowl-rz
            :bowl-radii bowl-radii
            :bowl-diameters (map #(* 2 %) bowl-radii)
            :top-z top-z
            :plate-x (or plate-x 9)
            :plate-y (or plate-y 9)
            :plate-z (+ top-z bowl-rz)
            :top-size [plate-x plate-y top-z]}
           explicit)))

(defn- minimal-shell-sequences
  "Positive and negative stages of a minimal keycap shell.
  The positive sequences is extended away from the switch by the top plate."
  [options]
  (let [{:keys [plate-x plate-y plate-z] :as merged} (minimal-options options)
        [positive negative] (rounded-frames (tight-shell-sequence merged))]
    [(cons positive
           (rounded-block (merge merged {:footprint [plate-x plate-y]
                                         :z-thickness plate-z})))
     negative]))

(defn- bowl-model
  "A sphere for use as negative space."
  [{:keys [bowl-radii bowl-plate-offset] :as options}]
  (when bowl-radii
    (let [{:keys [top-z bowl-rz bowl-diameters]} (minimal-options options)]
      (model/translate [0 0 (+ top-z bowl-rz bowl-plate-offset)]
        (model/resize bowl-diameters
          (model/sphere 3))))))  ; Low detail for quick previews.

(defn- minimal-body
  "A minimal (tight) keycap body with a skirt descending from a top plate.
  The top plate rises at the edges to form a bowl. The ‘top-size’ argument
  describes the plate, including the final thickness of the plate at its
  center. Nils in ‘top-size’ are simply replaced with a constant since the
  ‘slope’ argument has no meaning for a minimal cap. The ‘bowl-radii’ argument
  describes the sphere used as a negative to carve out the bowl."
  [{:keys [skirt-length] :as options}]
  (let [merged-opts (minimal-options options)
        [positive negative] (minimal-shell-sequences merged-opts)]
    (model/difference
      (model/intersection
        (maybe/difference
          (util/loft positive)
          (bowl-model merged-opts)))
      (switch-body merged-opts)
      (vaulted-ceiling merged-opts)
      (model/intersection  ; Make sure the inner negative cuts off at z = 0.
        (util/loft negative)
        (model/translate [0 0 -100]
          (model/cube 200 200 200)))
      ;; Cut everything before hitting the mounting plate:
      (model/translate [0 0 (- -100 skirt-length)]
        (model/cube 200 200 200)))))

(defn- stem-builder
  [{:keys [switch-type] :as options} pred]
  (let [data (switch-data switch-type :stem)]
    (map #(stem-body-cuboid options (get data %))
         (section-keys data pred))))

(defn- stem-model
  [options]
  (maybe/difference
    (apply maybe/union (stem-builder options positives))
    (apply maybe/union (stem-builder options negatives))))

(defn- horizontal-support
  "A cross connecting the stem and skirt. The purpose of this structure is to
  increase the surface contact between bed and cap while stabilizing the
  delicate stem in particular."
  [{:keys [switch-type nozzle-width horizontal-support-height] :as options}]
  (let [[stem-x stem-y] (stem-footprint switch-type 0)
        [skirt-x skirt-y] (map dec (data/skirt-footprint options))]  ;; Rough!
    (model/translate [0 0 (+ (print-bed-level options)
                             (/ horizontal-support-height 2))]
      (model/difference
        (model/union
          (model/cube skirt-x nozzle-width horizontal-support-height)
          (model/cube nozzle-width skirt-y horizontal-support-height))
        (model/cube stem-x stem-y horizontal-support-height)))))

(defn- skirt-support
  "A hollow outer perimeter beneath the skirt."
  [{:keys [switch-type style skirt-length nozzle-width] :as options}]
  (let [stem-z (stem-length switch-type)
        difference (- stem-z skirt-length)
        merged-opts (minimal-options options)
        sequence (case style
                   :minimal (minimal-shell-sequences merged-opts))
        outline (->> sequence
                     (first)
                     (util/loft)
                     (model/translate [0 0 skirt-length])
                     (model/cut))]
    (model/translate [0 0 (- (+ skirt-length difference))]
      (model/extrude-linear {:height difference, :center false}
        (model/difference
          outline
          (model/offset (- nozzle-width) outline))))))

(defn- stem-support
  "A completely hollow rectangular support structure with the width of the
  printer nozzle, underneath the keycap stem."
  [{:keys [switch-type skirt-length nozzle-width] :as options}]
  (let [stem-z (stem-length switch-type)
        difference (- skirt-length stem-z)
        footprint (stem-footprint switch-type 0)
        [foot-xₒ foot-yₒ] footprint
        [foot-xᵢ foot-yᵢ] (map #(max 0 (- % (* 2 nozzle-width))) footprint)]
    (model/translate [0 0 (+ (print-bed-level options) (/ difference 2))]
      (maybe/difference
        (model/cube foot-xₒ foot-yₒ difference)
        (when (every? pos? [foot-xᵢ foot-yᵢ])
          (model/cube foot-xᵢ foot-yᵢ difference))))))

(defn- support-model
  [{:keys [switch-type style skirt-length] :as options}]
  (let [stem-z (stem-length switch-type)]
    (maybe/union
      (horizontal-support options)
      (cond
        (< skirt-length stem-z) (skirt-support options)
        (> skirt-length stem-z) (stem-support options)))))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn keycap
  "A model of one keycap. Resolution is left at OpenSCAD defaults throughout.
  For a guide to the parameters, see README.md."
  [{:keys [switch-type style skirt-length supported sectioned]
    :or {switch-type (:switch-type data/option-defaults)
         style (:style data/option-defaults)}
    :as input-options}]
  {:pre [(spec/valid? ::data/keycap-parameters input-options)]}
  (let [options (merge data/option-defaults
                       {:skirt-length (data/default-skirt-length switch-type)}
                       input-options)]
    (maybe/intersection
      (maybe/union
        (case style
          :maquette (maquette-body options)
          :minimal (minimal-body options))
        (when-not (= style :maquette)
          (stem-model options))
        (when (and supported (not (= style :maquette)))
          (support-model options)))
      (when sectioned
        (model/translate [100 0 0]
          (model/cube 200 200 200))))))
