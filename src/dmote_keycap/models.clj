;;; Geometry.

(ns dmote-keycap.models
  (:require [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [scad-clj.model :as model]
            [scad-tarmi.core :refer [abs π √] :as tarmi]
            [scad-tarmi.dfm :refer [error-fn]]
            [scad-tarmi.maybe :as maybe]
            [scad-tarmi.util :as util]
            [dmote-keycap.schema :as schema]
            [dmote-keycap.data :as data]
            [dmote-keycap.measure :as measure]
            [dmote-keycap.legend :as legend]
            [dmote-keycap.misc :refer [deep-merge]]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

;; Semi-arbitrary internal constants.
(def wafer 0.01)
(def plenty 100)
(def big (* 2 plenty))
(def color-legend [0.2 0.3 0.4])

(defn- third [coll] (nth coll 2))
(defn- override-each [over coll] (map (partial merge over) coll))

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
          (measure/switch-height switch-type))))

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
      (apply model/cube (conj (mapv measure/key-length unit-size) wafer)))))

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
    {0 [(measure/switch-footprint switch-type)]}
    (data/switch-parts switch-type)))

(defn- rectangular-sections
  "Simplified switch sections for a squarish outer body of a keycap.
  Return a map of layer heights (vertical measurements) to 2-tuples of
  footprints (horizontal measurements at layer height)."
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
  (let [r (min (/ x 2) (/ y 2) radius)
        initial #(max wafer (- % (* 2 r)))]
    (->> (model/square (initial x) (initial y))
         (maybe/offset r))))

(defn- rounded-square
  [{:keys [footprint radius xy-offset] :or {radius 1.8, xy-offset 0}}]
  {:pre [(spec/valid? ::tarmi/point-2d footprint)
         (number? radius)
         (number? xy-offset)]}
  (inset-corner (map #(+ % (* 2 xy-offset)) footprint) radius))

(defn- rounded-stack [option-sets] (mapv rounded-square option-sets))

(defn- inflate
  "Extrude one 2D slice of a keycap into 3D, at the right height."
  [{:keys [z-offset z-thickness] :or {z-offset 0, z-thickness wafer}}
   shape-2d]
  {:pre [(number? z-offset)]}
  (->> shape-2d
    (model/extrude-linear {:height z-thickness, :center false})
    (maybe/translate [0 0 z-offset])))

(defn- rounded-block [options] (inflate options (rounded-square options)))

(defn- pillar
  "Take a sequence of z-axis data maps and a matching sequence of 2D shapes.
  Combine them into a sequence of 3D shapes."
  [z-data shapes-2d]
  (map-indexed
    (fn [index z-datum] (inflate z-datum (get shapes-2d index)))
    z-data))

(defn- switch-body-cube
  [{:keys [switch-type error-body-positive]}
   part-name]
  (let [{:keys [x y z]} (get-in data/switches
                          [switch-type :body part-name :size])
        compensator (error-fn error-body-positive)]
    (model/translate [0 0 (- (/ z 2) (measure/switch-height switch-type))]
      (model/cube (compensator x) (compensator y) z))))

(defn- stem-bodypart
  "Similar to switch-body-cube but for one part of a stem."
  [{:keys [error-stem-positive error-stem-negative]}
   part-properties]
  {:pre [(map? part-properties)
         (:size part-properties)]}
  (let [{:keys [x y z]} (:size part-properties)
        error (if (:positive part-properties) error-stem-positive
                                              error-stem-negative)
        compensator (error-fn error)]
    (model/translate [0 0 (/ z -2)]
      (case (:shape part-properties)
        :cylinder (model/cylinder (compensator (/ x 2)) z)
        (model/cube (compensator x) (compensator y) z)))))

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
  material in the top plate of a tall minimal-style cap.
  When running with generated supports, there are roof trusses under the
  ceiling, bridging the inner and outer surfaces of the vaulting early, to
  prevent a printer head from bending the stem."
  [{:keys [switch-type top-size nozzle-width supported truss-offset
           error-stem-positive error-body-positive]}]
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
          inner-profile)
        (when supported
          (model/translate [0 0 (+ plenty truss-offset)]
            (model/union
              (model/cube nozzle-width big big)
              (model/cube big nozzle-width big))))))))

(defn- tight-shell-sequence
  [{:keys [switch-type] :as options}]
  (let [rectangles-by-z (rectangular-sections switch-type)]
    (reduce
      (fn [coll z]
        (conj coll
          (merge options
                 {:footprint (get rectangles-by-z z)
                  :z-offset (- z (measure/switch-height switch-type))})))
      []
      (reverse (sort (keys rectangles-by-z))))))

(defn- bowl?
  "True if a bowl-shaped top has been requested."
  [{:keys [bowl-radii]}]
  (and (some? bowl-radii) (every? some? bowl-radii)))

(defn- sacrificial-depth
  "Compute how much vertical material to add for the bowl to be cut from.
  The bowl is cut *to* the depth of the user-configured top, no further.
  The following specifies extra height for the bowl to cut *through*.
  It’s based on the z-axis radius of the bowl, together with the diagonal of
  the user-configured top.
  The formula is intended to prevent absurdity in the case of a bowl
  thinner or much wider than the user-configured top itself."
  [{:keys [top-size bowl-radii]}]
  (let [rz (third bowl-radii)
        [sx sy _] top-size
        square #(Math/pow % 2)
        d (√ (+ (square sx) (square sy)))  ; Diagonal across top, between corners.
        c (√ (- (square rz) (square (/ d 2))))]  ; Half of chord of circle over corners.
    (- rz (if (Double/isNaN c) 0 c))))

(defn- sacrificial-surface
  "Compute a horizontal extent on one side, and matching vertical extent,
  of material from which to cut a bowl."
  [θ height full-side]
  (let [s (/ full-side 2)  ; Trigonometry is done on offset from centre.
        ratio (Math/tan θ)  ; Required ratio of height over half-side.
        z (min height (* ratio s))]
    (if (< z height)
      ;; A pointy pyramid less tall than the potential depth of cut through it.
      [wafer z]
      ;; A ziggurat at full height. Width at top is governed by ratio.
      [(* 2 (- s (/ z ratio))) z])))

(defn- sarificial-block
  "Find the size of a block of material from which to cut a bowl."
  [{:keys [top-size slope] :as options}]
  (let [sacrifice (partial sacrificial-surface
                           (* (/ π 2) slope)
                           (sacrificial-depth options))
        xz (sacrifice (first top-size))
        yz (sacrifice (second top-size))]
    ;; If the requested top is uneven, compromise on the slope.
    [(first xz) (first yz) (/ (+ (second xz) (second yz)) 2)]))

(defn- top-sizes
  "List atomic positive elements of the top of a keycap."
  ;; In order from lowest to highest.
  [{:keys [top-size] :as options}]
  (if (bowl? options)
    ;; Add a taller block from which the bowl is to be cut.
    [top-size (update (sarificial-block options) 2 #(+ % (third top-size)))]
    ;; No bowl, no rim.
    [top-size]))

(defn- tuple-to-pillarspec [[x y z]] {:footprint [x y], :z-thickness z})

(defn- minimal-shell-sequences
  "The four layers of a minimal keycap shell.
  Each layer is a sequence of 3D shapes ready for combination by lofting.
  They are returned in order from outermost to innermost.
  The first two are extended away from the switch by the top plate."
  [{:keys [skirt-thickness skirt-space legend] :as options}]
  (let [engraving-depth (:depth legend)
        inner-shell (tight-shell-sequence options)
        outer-top (->> (top-sizes options)
                       (map tuple-to-pillarspec)
                       (map (partial merge options)))
        inner-top (map #(assoc % :xy-offset (- engraving-depth)) outer-top)
        outer-shell (override-each {:xy-offset (+ skirt-thickness skirt-space)}
                                   inner-shell)
        outer-stack (rounded-stack outer-shell)]
    [;; The blocky exterior of the keycap.
     (cons (pillar inner-shell outer-stack) (map rounded-block outer-top))
     ;; The depth to which any engraving will be done.
     (cons (pillar inner-shell
                   (mapv (partial model/offset (- engraving-depth))
                         outer-stack))
           (map rounded-block inner-top))
     ;; The interior hollow of the skirt, drawn in from the outermost layer.
     (pillar inner-shell (mapv (partial model/offset (- skirt-thickness))
                               outer-stack))
     ;; The shape of the switch, for printer error compensation.
     (pillar inner-shell (rounded-stack inner-shell))]))

(defn- engraved-legend
  "An extrusion from a 2D legend image into a 3D negative."
  [error filepath]
  (->> filepath
    (model/import)
    (maybe/offset (- error))
    (model/extrude-linear {:height (measure/key-length 1)  ; Rough.
                           :convexity 6})))

(defn- bowl-model
  "A sphere for use as negative space.
  Use low detail for quick previews, except on a spherical-cap."
  [{:keys [top-size bowl-radii bowl-plate-offset z-override]}]
  (let [bowl-z (or z-override (third bowl-radii))]
    (model/translate [0 0 (+ (third top-size) bowl-z bowl-plate-offset)]
      (model/resize (map #(* 2 %) bowl-radii)  ; Bowl diameters.
        (model/sphere (max (apply min bowl-radii) 3))))))

(defn- bowl-with-legend
  "Negative space with a legend protruding from a spheroid."
  [{:keys [bowl-radii legend error-top-negative] :as options}]
  (let [overrides {:bowl-radii (mapv #(+ (:depth legend) %) bowl-radii)
                   :z-override (third bowl-radii)}]
    (model/union
      (bowl-model options)
      (model/color color-legend
       (model/intersection
         (bowl-model (merge options overrides))
         (engraved-legend error-top-negative (get-in legend [:faces :top])))))))

(defn- legend-without-bowl
  "Negative space constituting the top-face legend without a curvature."
  [{:keys [legend top-size error-top-negative]}]
  (let [depth (:depth legend)
        filepath (get-in legend [:faces :top])]
   (model/translate [0 0 wafer]  ; Cleaner OpenSCAD preview.
     (model/color color-legend
       (model/intersection
         (model/translate [0 0 (- (third top-size) (/ depth 2))]
           (model/cube big big depth))
         (engraved-legend error-top-negative filepath))))))

(defn- has-finalized-legend?
  [options face]
  (get-in options [:legend :faces face]))

(defn- top-face
  "Negative space shaping the topmost surface of a cap, whether flat or not."
  [options]
  (let [bowl (bowl? options)
        motif (has-finalized-legend? options :top)]
    (cond
      (and bowl motif) (bowl-with-legend options)
      bowl (bowl-model options)
      motif (legend-without-bowl options))))

(defn- side-face
  "A model of the legend on one side of a cap.
  The 3D image is tilted for a rough match against the slope of the key.
  This function centers the image roughly at z = 0, without adaptation to
  short skirts or tall tops. Rotation of the image is also not supported
  from parameters: All such modifications currently need to happen in the 2D
  image itself."
  [{:keys [legend skirt-length slope unit-size error-side-negative] :as options} face]
  (when (has-finalized-legend? options face)
    (let [{:keys [coord-mask z-angle]} (face data/faces)
          masked-size (mapv * coord-mask unit-size)
          real-size (mapv #(/ (measure/key-length %) 2) masked-size)
          unit-length (abs (first (remove zero? masked-size)))
          slope-tilt (- (Math/atan (/ (* slope unit-length) skirt-length)))]
      (->> (get-in legend [:faces face])
        (engraved-legend error-side-negative)
        (model/rotate [slope-tilt 0 0])
        (model/rotate [(/ π 2) 0 z-angle])
        (model/translate (conj real-size 0))))))

(defn- side-faces
  "Negative space shaping the sides of a cap for engraved legends."
  [{:keys [legend] :as options}]
  (apply maybe/union
    (map (partial side-face options) (keys (dissoc (:faces legend) :top)))))

(defn- minimal-body
  "A minimal (tight) keycap body with a skirt descending from a top plate.
  The ‘top-size’ argument describes the plate, including the final thickness
  of the plate at its center."
  [{:keys [skirt-length shell-sequence-fn] :as options}]
  (let [[outermost intermediate interior _] (shell-sequence-fn options)
        side-legends (side-faces options)]
    (model/difference
      (maybe/difference
        (util/loft outermost)
        (top-face options)
        (when side-legends
          (model/color color-legend
            (model/difference
              side-legends
              (util/loft intermediate)))))
      (switch-body options)
      (vaulted-ceiling options)
      (model/intersection  ; Make sure the inner negative cuts off at z = 0.
        (util/loft interior)
        (model/translate [0 0 (- plenty)]
          (model/cube big big big)))
      ;; Cut everything before hitting the mounting plate:
      (model/translate [0 0 (- (- plenty) skirt-length)]
        (model/cube big big big)))))

(defn- stem-builder
  [{:keys [switch-type] :as options} pred]
  (let [data (switch-data switch-type :stem)]
    (map #(stem-bodypart options (get data %))
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
        [skirt-x skirt-y] (mapv measure/key-length unit-size)
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
  (let [footprint (stem-footprint switch-type 0)
        datum (fn [key default]
                (get-in data/switches [switch-type :support :stem key] default))]
    (model/translate [0 0 (print-bed-level options)]
      (model/extrude-linear {:height (- (abs (print-bed-level options))
                                        (stem-length switch-type))
                             :center false}
        (maybe/rotate [0 0 (datum :angle 0)]
          (model/difference
            (maybe/offset (* -1 nozzle-width (datum :inset-line-count 0))
              (apply model/square footprint))
            (maybe/offset (* -1 nozzle-width (+ (datum :inset-line-count 0)
                                                (datum :thickness-line-count 1)))
              (apply model/square footprint))))))))

(defn- support-model
  [{:keys [switch-type skirt-length] :as options}]
  (let [stem-z (stem-length switch-type)]
    (maybe/union
      (horizontal-support options)
      (cond
        (< skirt-length stem-z) (skirt-support options)
        (> skirt-length stem-z) (stem-support options)))))

(defn- to-filepath
  "Produce a relative file path, from the current working directory to a
  place where OpenSCAD will find the file by its name alone. In this default
  version, use the standard scad-app SCAD output directory."
  [filename]
  (str (io/file "output" "scad" filename)))

(defn- enrich-options
  "Take merged global-default and explicit user arguments. Merge these further
  with defaults that depend on other options."
  [{:keys [switch-type style] :as explicit}]
  (merge {:top-size [nil nil 1]
          :top-rotation [0 0 0]
          :bowl-radii [nil nil nil]
          :skirt-length (measure/default-skirt-length switch-type)
          :stem-fn stem-model
          :importable-filepath-fn to-filepath
          :support-fn support-model}
         ;; Add any constants specific to the style.
         (data/style-defaults style {})
         ;; Add logic specific to the style.
         (case style
           :maquette {:body-fn maquette-body
                      :stem-fn (constantly nil)
                      :support-fn (constantly nil)}
           :minimal {:body-fn minimal-body
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
      :maquette (conj (mapv #(* slope (measure/key-length %)) unit-size)
                      (third old))
      :minimal (mapv #(if (some? %1) %1 9) old))))

(defn- finalize-face-source
  [path-fn basename face
   {:keys [unimportable importable char text-options]}]
  (let [intname (format "%s_%s" basename (name face))]
    (cond
      importable   importable
      unimportable (legend/make-importable path-fn intname unimportable)
      char         (legend/make-from-char path-fn intname char text-options))))

(defn- finalize-legend
  "Generate file paths for all configured faces with a legend."
  [{:keys [filename importable-filepath-fn]} old]
  (assoc old :faces
    (into {}
      (for [[face properties] (:faces old)]
        (if-let [path (finalize-face-source
                        importable-filepath-fn filename face properties)]
          [face path])))))

(defn- interpolate-options
  "Resolve ambiguities in user input."
  [options]
  (-> options
      (update :top-size (partial finalize-top options))
      (update :legend (partial finalize-legend options))))

(defn- finalize-options
  "Reconcile explicit user arguments with defaults at various levels."
  [explicit-arguments]
  (->> explicit-arguments
       (deep-merge data/option-defaults)
       enrich-options
       interpolate-options))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn keycap
  "A model of one keycap. Resolution is left at OpenSCAD defaults throughout.
  For a guide to the parameters, see README.md."
  [explicit-arguments]
  {:pre [(spec/valid? ::schema/keycap-parameters explicit-arguments)]}
  (let [{:keys [supported sectioned body-fn stem-fn support-fn]
         :as options} (finalize-options explicit-arguments)]
    (maybe/intersection
      (maybe/union
        (body-fn options)
        (stem-fn options)
        (when supported
          (support-fn options)))
      (when sectioned
        (model/translate [plenty 0 0]
          (model/cube big big big))))))
