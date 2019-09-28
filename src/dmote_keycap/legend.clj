;;; 2D vector graphics for markings on keycaps.

(ns dmote-keycap.legend
  (:require [clojure.java.shell :refer [sh]]
            [hiccup2.core :refer [html]]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(defn- text-svg
  "A string representing an SVG image with a text element.
  The nominal size of this document, and positions within it,
  are based on the assumption that OpenSCAD will display elements
  from the SVG file even if they fall outside the viewbox."
  [legend]
  (html
    [:svg
     {:xmlns "http://www.w3.org/2000/svg"
      :width "1mm"
      :height "1mm"
      :viewBox "0 0 1 1"
      :version "1.1"}
     [:text
      {:style "font-size:5mm;font-family:'Bitstream Vera Sans Mono';text-anchor:middle;text-align:center;dominant-baseline:middle"
       :x "0"
       :y "1"}
      legend]]))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn text-to-file
  [basename face]
  (let [filename (str basename "-face-raw.svg")]
    (spit filename (text-svg face))
    filename))

(defn simple
  []
  (let [filename-in (text-to-file "j" "J")
        filename-out "legend.svg"
        outcome (sh "inkscape"
                    filename-in
                    (str "--export-plain-svg=" filename-out)
                    "--export-text-to-path")]
    (assert (zero? (:exit outcome)))))
