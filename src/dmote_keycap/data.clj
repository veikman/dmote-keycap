;;; Constants used for constructing keycaps.

(ns dmote-keycap.data
  (:require [scad-tarmi.core :refer [π] :as tarmi]))

;;;;;;;;;;;;;;;
;; Constants ;;
;;;;;;;;;;;;;;;

(def mount-1u 19.05)

;; Typical 1U keycap width and depth, approximate.
(def key-width-1u 18.25)
(def key-margin (/ (- mount-1u key-width-1u) 2))

;; Reusable pieces of switch data.
(def mx-cross
  {:cross-x {:size {:x 4,   :y 1.25, :z 3.6} :positive false}
   :cross-y {:size {:x 1.1, :y 4,    :z 3.6} :positive false}})
(def mx-body
  {:top  {:size {:x 10.2, :y 11,   :z 6.6}}
   :core {:size {:x 14.7, :y 14.7, :z 1}}
   :base {:size {:x 15.6, :y 15.6, :z 0.7}}})

;; Switch data here is based on real-world observation, not purely on data
;; sheets. Some components are modelled for printability and may be
;; incompatible with some versions of real switches.
;; Data under :support is auxiliary and does not describe real switches.
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
     :stem (merge mx-cross
             {:shell        {:size {:x 6,     :y 6,     :z 3.6}
                             :shape :cylinder, :positive true}})
     :body mx-body
     :support {:stem {:inset-line-count 1.5,  :angle (/ π 4)}}}
   :rect-mx
    {:travel 4
     :stem (merge mx-cross
             {:shell        {:size {:x 7,     :y 5.25,  :z 3.6}
                             :positive true}})
     :body mx-body
     :support {:stem {:inset-line-count 0.8}}}})

;; Face data concerns how legends are placed on the sides of keys.
(def face-keys [:top :north :east :south :west])
(def faces
  {:north {:coord-mask [ 0  1], :z-angle π}
   :east  {:coord-mask [ 1  0], :z-angle (/ π 2)}
   :south {:coord-mask [ 0 -1], :z-angle 0}
   :west  {:coord-mask [-1  0], :z-angle (* 3/2 π)}})

;; The keycap function exposed by dmote-keycap.models takes a number of options
;; whose global default values are exposed here.
;; Together with the default 1 y offset, the 1x1 scale of generated SVG
;; and the dominant baseline set here, generated legends should be
;; nearly vertically centered in relation to the font. Note, however, that SVG
;; by itself does not provide options for vertically centering letters or
;; symbols. As the font and font size change, the y offset must also change.
(def text-style-defaults {:font-size "1mm"
                          :font-family "DejaVu Sans Mono"
                          :text-anchor "middle"
                          :text-align "center"
                          :dominant-baseline "middle"})
(def style-defaults {:minimal {:top-size [9 9 1]
                               :bowl-radii [15 10 2]}})
(def option-defaults
  {:filename "cap"
   :style :minimal
   :switch-type :mx
   :unit-size [1 1]
   :slope 0.73
   :bowl-plate-offset 0
   :skirt-thickness 0.8
   :skirt-space 0.4
   :legend {:depth 0.4  ; Suitable for SLA engraving.
            :faces (into {}
                     (for [f face-keys]
                       [f {:text-options {:style text-style-defaults
                                          :x "0"
                                          :y "1"}}]))}
   :nozzle-width 0.4
   :horizontal-support-height 0.8
   :truss-offset 0
   :error-body-positive 0
   :error-side-negative 0
   :error-stem-negative 0
   :error-stem-positive 0
   :error-top-negative 0})


;;;;;;;;;;;;;;;;;;;;;;;;
;; Accessor functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;

(defn switch-parts
  "The keyword names of the parts of a switch body."
  [switch-type]
  (keys (get-in switches [switch-type :body])))
