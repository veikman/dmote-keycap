;;; Constants and very basic functions concerning keycaps.

(ns dmote-keycap.data
  (:require [clojure.spec.alpha :as spec]
            [scad-tarmi.core :as tarmi]))

;;;;;;;;;;;;;;;
;; Constants ;;
;;;;;;;;;;;;;;;

(def mount-1u 19.05)

;; Typical 1U keycap width and depth, approximate.
(def key-width-1u 18.25)
(def key-margin (/ (- mount-1u key-width-1u) 2))

;; Switch data here is based on real-world observation, not purely on data
;; sheets. Some components are modelled for printability and may be
;; incompatible with some versions of real switches.
(def switches
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
     :stem {:shell          {:size {:x 7,     :y 5.25,  :z 3.6}
                             :positive true}
            :cross-x        {:size {:x 4,     :y 1.25,  :z 3.6}
                             :positive false}
            :cross-y        {:size {:x 1.1,   :y 4,     :z 3.6}
                             :positive false}}
     :body {:top            {:size {:x 10.2,  :y 11,    :z 6.6}}
            :core           {:size {:x 14.7,  :y 14.7,  :z 1}}
            :base           {:size {:x 15.6,  :y 15.6,  :z 0.7}}}}})

;; The keycap function exposed by dmote-keycap.models takes a number of options
;; whose global default values are exposed here.
(def option-defaults {:filename "cap"
                      :style :minimal
                      :switch-type :alps
                      :unit-size [1 1]
                      :slope 0.73
                      :bowl-plate-offset 0
                      :skirt-thickness 2.1
                      :nozzle-width 0.5
                      :horizontal-support-height 0.5
                      :error-body-positive -0.5
                      :error-stem-positive 0
                      :error-stem-negative 0})


;;;;;;;;;;;;
;; Schema ;;
;;;;;;;;;;;;

(spec/def ::style #{:maquette :minimal})
(spec/def ::switch-type (set (keys switches)))
(spec/def ::unit-size ::tarmi/point-2d)
(spec/def ::top-size
  (spec/tuple (spec/nilable number?) (spec/nilable number?) number?))
(spec/def ::top-rotation ::tarmi/point-3d)
(spec/def ::bowl-radii ::tarmi/point-3d)
(spec/def ::bowl-plate-offset number?)
(spec/def ::skirt-thickness number?)
(spec/def ::skirt-length (spec/and number? #(>= % 0)))
(spec/def ::slope (spec/and number? #(>= % 0)))
(spec/def ::nozzle-width (spec/and number? #(> % 0)))
(spec/def ::horizontal-support-height (spec/and number? #(>= % 0)))
(spec/def ::error-stem-positive number?)
(spec/def ::error-stem-negative number?)
(spec/def ::error-body-positive number?)
(spec/def ::sectioned boolean?)
(spec/def ::supported boolean?)

;; A composite spec for all valid parameters going into the keycap model.
(spec/def ::keycap-parameters
  (spec/keys :opt-un [::style ::switch-type ::unit-size
                      ::top-size ::top-rotation
                      ::bowl-radii ::bowl-plate-offset
                      ::skirt-thickness ::skirt-length ::slope
                      ::nozzle-width ::horizontal-support-height
                      ::error-stem-positive ::error-stem-negative
                      ::error-body-positive ::sectioned ::supported]))

;;;;;;;;;;;;;;;
;; Functions ;;
;;;;;;;;;;;;;;;

(declare switch-dimension)  ; Internal.

(defn key-length [units] (- (* units mount-1u) (* 2 key-margin)))

(defn switch-parts
  "The keyword names of the parts of a switch body."
  [switch-type]
  (keys (get-in switches [switch-type :body])))

(defn switch-height
  "The total height of a switch’s body over the mounting plate."
  [switch-type]
  (switch-dimension switch-type :z))

(defn default-skirt-length
  "The default height of a keycap over the mounting plate is 1 mm less than the
  height of the switch. This is a rough estimate based on frobnicating OEM
  caps."
  [switch-type]
  (dec (switch-height switch-type)))

(defn switch-footprint
  "The xy footprint of a switch’s body on the mounting plate. This is not to be
  confused with notches extending from the body to rest on top of the plate."
  [switch-type]
  [(switch-dimension switch-type :x)
   (switch-dimension switch-type :y)])

(defn skirt-footprint
  "The maximum horizontal size of a keycap.
  This measurement should describe the keycap at its widest point, coinciding
  with the lower edge of the skirt. The function is exposed to allow for
  clearing negative space around the keycap in a keyboard model."
  [options]
  {:pre [(spec/valid? ::keycap-parameters options)]
   :post [(spec/valid? ::tarmi/point-2d %)]}
  (let [options-final (merge option-defaults options)
        {:keys [style switch-type unit-size skirt-thickness]} options-final]
    (if (= style :minimal)
      (mapv #(+ % skirt-thickness) (switch-footprint switch-type))
      (mapv key-length unit-size))))

(defn plate-to-stem-end
  "The distance from the switch mount plate to the end of the switch stem,
  in the resting (open) state of the switch.
  This is exposed because dmote-keycap models place z 0 at the end of the
  stem, not on the mounting plate."
  [switch-type]
  (+ (switch-height switch-type)
     (get-in switches [switch-type :travel])))

(defn pressed-clearance
  "The height of the skirt of a keycap above the mounting plate, depressed."
  [switch-type skirt-length]
  {:pre [(spec/valid? ::switch-type switch-type)
         (spec/valid? number? skirt-length)]}
  (- (switch-height switch-type) skirt-length))

(defn resting-clearance
  "The height of the skirt of a keycap above the mounting plate, at rest."
  [switch-type skirt-length]
  (+ (pressed-clearance switch-type skirt-length)
     (get-in switches [switch-type :travel])))


;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(defn- switch-dimension
  "A switch’s body over the mounting plate."
  [switch-type dimension]
  {:pre [(spec/valid? ::switch-type switch-type)]
   :post [(number? %)]}
  (apply max (map #(get-in switches [switch-type :body % :size dimension])
                  (switch-parts switch-type))))
