;;; Prediction of model measurements through calculations upon package data.

(ns dmote-keycap.measure
  (:require [clojure.spec.alpha :refer [valid?]]
            [scad-tarmi.core :as tarmi]
            [dmote-keycap.data :refer [mount-1u key-margin switches
                                       option-defaults switch-parts]]
            [dmote-keycap.schema :as schema]))

(declare switch-dimension)  ; Internal.

;;;;;;;;;;;;;;;
;; Functions ;;
;;;;;;;;;;;;;;;

(defn key-length [units] (- (* units mount-1u) (* 2 key-margin)))

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
  {:pre [(valid? ::schema/keycap-parameters options)]
   :post [(valid? ::tarmi/point-2d %)]}
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
  {:pre [(valid? ::schema/switch-type switch-type)
         (number? skirt-length)]}
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
  {:pre [(valid? ::schema/switch-type switch-type)]
   :post [(number? %)]}
  (apply max (map #(get-in switches [switch-type :body % :size dimension])
                  (switch-parts switch-type))))
