;;; 2D vector graphics for markings on keycaps.

(ns dmote-keycap.legend
  (:require [clojure.string :refer [join]]
            [clojure.java.shell :refer [sh]]
            [hiccup2.core :refer [html]]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(defn- to-css
  "Present a Clojure map to a CSS-like style specification."
  [mapping]
  (join ";" (for [[key value] mapping] (str (name key) ":" value))))

(defn- text-svg
  "A string representing an SVG image with a text element.
  The nominal size of this document, and positions within it,
  are based on the assumption that OpenSCAD will display elements
  from the SVG file even if they fall outside the viewbox."
  [legend style]
  (html
    [:svg
     {:xmlns "http://www.w3.org/2000/svg"
      :width "1mm"
      :height "1mm"
      :viewBox "0 0 1 1"
      :version "1.1"}
     [:text
      {:style (to-css style)
       :x "0"
       :y "1"}
      legend]]))

(defn- path-filename [basename] (str basename "_path.svg"))

(defn- convert-to-plain-svg
  "Simplify text elements in an SVG file to paths.
  This requires Inkscape and needs input and output file paths.
  It makes the content more readable to OpenSCAD."
  [in out]
  (let [cmd ["inkscape" in "--export-plain-svg" out "--export-text-to-path"]]
    (when-not (zero? (:exit (apply sh cmd)))
      (throw (ex-info "File conversion with Inkscape failed" {:command cmd})))
    out))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn make-importable
  "Author an SVG file from another SVG. Return the new filename."
  [filepath-fn basename filepath-text]
  (let [filename-out (path-filename basename)]
    (convert-to-plain-svg filepath-text (filepath-fn filename-out))
    filename-out))

(defn make-from-char
  "Author an SVG file from a string, with an intermediate artefact."
  [filepath-fn basename legend]
  (let [filepath-text (filepath-fn (str basename "_text.svg"))
        filename-out (path-filename basename)
        style {:font-size "1mm"
               :font-family "'Bitstream Vera Sans Mono'"
               :text-anchor "middle"
               :text-align "center"
               :dominant-baseline "middle"}]
    (spit filepath-text (text-svg legend style))
    (convert-to-plain-svg filepath-text (filepath-fn filename-out))
    filename-out))
