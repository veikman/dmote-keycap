;;; Geometry.

(ns dmote-keycap.models
  (:require [clojure.spec.alpha :as spec]
            [scad-clj.model :as model]
            [scad-tarmi.core :refer [abs] :as tarmi]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]
            [dmote-keycap.data :as data]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(def wafer 0.01)
(def plenty 100)
(def big (* 2 plenty))

(defn- third [coll] (nth coll 2))

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
  relative to the top of the stem.
  This is built on the assumption that there is no need to build a keycap whose
  interior is taller than the body of its switch above the mounting plate."
  [{:keys [switch-type skirt-length]}]
  (- (min (max (stem-length switch-type) skirt-length)
          (data/switch-height switch-type))))

(defn- maquette-body
  "The shape of one keycap, greatly simplified.
  The simplification is so extensive that this keycap can only be used for
  previews in keyboard models. It is not hollow and therefore useless if
  printed.
  The default height and slope are based on a DSA profile. Passing a
  non-default ‘top-size’ and ‘top-rotation’ can provide a rough approximation
  of SA and OEM caps, etc."
  [{:keys [unit-size top-size top-rotation skirt-length]}]
  (model/hull
    (maybe/translate [0 0 (/ (third top-size) 2)]
      (maybe/rotate top-rotation
        (apply model/cube top-size)))
    (maybe/translate [0 0 (- skirt-length)]
      (apply model/cube (conj (mapv data/key-length unit-size) wafer)))))

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
  {:pre [(spec/valid? ::tarmi/point-2d footprint)]}
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
  [{:keys [switch-type top-size error-stem-positive error-body-positive]}]
  (let [peak-z (dec (third top-size))
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

(defn- minimal-shell-sequences
  "Positive and negative stages of a minimal keycap shell.
  The positive sequences is extended away from the switch by the top plate."
  [{:keys [top-size bowl-radii] :as options}]
  (let [[x y top-z] top-size
        z (+ top-z (third bowl-radii))
        [positive negative] (rounded-frames (tight-shell-sequence options))]
    [(cons positive
           (rounded-block (merge options {:footprint [x y]
                                          :z-thickness z})))
     negative]))

(defn- bowl-model
  "A sphere for use as negative space."
  [{:keys [top-size bowl-radii bowl-plate-offset]}]
  (let [bowl-z (third bowl-radii)]
    (when-not (zero? bowl-z)
      (model/translate [0 0 (+ (third top-size) bowl-z bowl-plate-offset)]
        (model/resize (map #(* 2 %) bowl-radii)  ; Bowl diameters.
          (model/sphere 3))))))  ; Low detail for quick previews.

(defn- minimal-body
  "A minimal (tight) keycap body with a skirt descending from a top plate.
  The top plate rises at the edges to form a bowl. The ‘top-size’ argument
  describes the plate, including the final thickness of the plate at its
  center. The ‘bowl-radii’ argument describes the sphere used as a negative to
  carve out the bowl."
  [{:keys [skirt-length shell-sequence-fn] :as options}]
  (let [[positive negative] (shell-sequence-fn options)]
    (model/difference
      (model/intersection
        (maybe/difference
          (util/loft positive)
          (bowl-model options)))
      (switch-body options)
      (vaulted-ceiling options)
      (model/intersection  ; Make sure the inner negative cuts off at z = 0.
        (util/loft negative)
        (model/translate [0 0 (- plenty)]
          (model/cube big big big)))
      ;; Cut everything before hitting the mounting plate:
      (model/translate [0 0 (- (- plenty) skirt-length)]
        (model/cube big big big)))))

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

(defn- minimal-skirt-perimeter
  "A 2D object describing the perimeter of the skirt where it cuts off."
  [{:keys [skirt-length shell-sequence-fn] :as options}]
  (->> options
       shell-sequence-fn
       first
       util/loft
       (model/translate [0 0 skirt-length])
       model/cut))

(defn- horizontal-support
  "A cross connecting the stem and skirt. The purpose of this structure is to
  increase the surface contact between bed and cap while stabilizing the
  delicate stem in particular.
  The mechanism that limits the extent of the cross is much more complicated
  than the cross itself: It’s anded with the overall outer profile of the
  keycap and with its outline at the end of the skirt, to ensure that no sharp
  edges extend outside the skirt even if the cutoff somehow occurs on a slope."
  [{:keys [switch-type unit-size nozzle-width horizontal-support-height
           shell-sequence-fn skirt-perimeter-fn]
    :as options}]
  (let [[stem-x stem-y] (stem-footprint switch-type 0)
        [skirt-x skirt-y] (mapv data/key-length unit-size)
        positive (first (shell-sequence-fn options))
        outline (skirt-perimeter-fn options)]
    (model/intersection
      (util/loft positive)
      (model/extrude-linear {:height plenty} outline)
      (model/translate [0 0 (+ (print-bed-level options)
                               (/ horizontal-support-height 2))]
        (model/difference
          (model/union
            (model/cube skirt-x nozzle-width horizontal-support-height)
            (model/cube nozzle-width skirt-y horizontal-support-height))
          (model/cube stem-x stem-y horizontal-support-height))))))

(defn- skirt-support
  "A hollow outer perimeter beneath the skirt."
  [{:keys [switch-type skirt-length nozzle-width skirt-perimeter-fn]
    :as options}]
  (let [stem-z (stem-length switch-type)
        difference (- stem-z skirt-length)
        outline (skirt-perimeter-fn options)]
    (model/translate [0 0 (- (+ skirt-length difference))]
      (model/extrude-linear {:height difference, :center false}
        (model/difference
          outline
          (model/offset (- nozzle-width) outline))))))

(defn- stem-support
  "A completely hollow rectangular support structure with the width of the
  printer nozzle, underneath the keycap stem."
  [{:keys [switch-type nozzle-width] :as options}]
  (let [stem-z (stem-length switch-type)
        difference (- (abs (print-bed-level options)) stem-z)
        footprint (stem-footprint switch-type 0)
        [foot-xₒ foot-yₒ] footprint
        [foot-xᵢ foot-yᵢ] (map #(max 0 (- % (* 2 nozzle-width))) footprint)]
    (model/translate [0 0 (+ (print-bed-level options) (/ difference 2))]
      (maybe/difference
        (model/cube foot-xₒ foot-yₒ difference)
        (when (every? pos? [foot-xᵢ foot-yᵢ])
          (model/cube foot-xᵢ foot-yᵢ difference))))))

(defn- support-model
  [{:keys [switch-type skirt-length] :as options}]
  (let [stem-z (stem-length switch-type)]
    (maybe/union
      (horizontal-support options)
      (cond
        (< skirt-length stem-z) (skirt-support options)
        (> skirt-length stem-z) (stem-support options)))))

(defn- enrich-options
  "Take merged global-default and explicit user arguments. Merge these further
  with defaults that depend on other options."
  [{:keys [switch-type style] :as explicit}]
  (merge {:top-size [nil nil 1]
          :top-rotation [0 0 0]
          :bowl-radii [0 0 0]
          :skirt-length (data/default-skirt-length switch-type)
          :stem-fn stem-model
          :support-fn support-model}
         (case style
           :maquette {:body-fn maquette-body
                      :stem-fn (constantly nil)
                      :support-fn (constantly nil)}
           :minimal {:top-size [9 9 1]
                     :bowl-radii [15 10 2]
                     :body-fn minimal-body
                     :shell-sequence-fn minimal-shell-sequences
                     :skirt-perimeter-fn minimal-skirt-perimeter})
         explicit))

(defn- finalize-top
  "Pick a top size where the user has omitted some part(s) of the parameter.
  In the maquette style, the ‘slope’ argument is used to calculate the size
  of the top plate, whereas in the minimal style, nils in ‘top-size’ are
  simply replaced with a constant."
  [{:keys [style unit-size slope]} old]
  (if (every? some? old)
    old
    (case style
      :maquette (conj (mapv #(* slope (data/key-length %)) unit-size)
                      (third old))
      :minimal (mapv #(if (some? %1) %1 9) old))))

(defn- interpolate-options
  "Resolve ambiguities in user input."
  [options]
  (update options :top-size (partial finalize-top options)))

(defn- finalize-options
  "Reconcile explicit user arguments with defaults at various levels."
  [explicit-arguments]
  (->> explicit-arguments
       (merge data/option-defaults)
       enrich-options
       interpolate-options))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn keycap
  "A model of one keycap. Resolution is left at OpenSCAD defaults throughout.
  For a guide to the parameters, see README.md."
  [explicit-arguments]
  {:pre [(spec/valid? ::data/keycap-parameters explicit-arguments)]}
  (let [{:keys [supported sectioned body-fn stem-fn support-fn]
         :as options} (finalize-options explicit-arguments)]
    (maybe/intersection
      (maybe/union
        (body-fn options)
        (stem-fn options)
        (when supported
          (support-fn options)))
      (when sectioned
        (model/translate [100 0 0]
          (model/cube 200 200 200))))))
