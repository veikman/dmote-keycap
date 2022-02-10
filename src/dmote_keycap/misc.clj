;;; General logic.

(ns dmote-keycap.misc
  (:require [scad-tarmi.core :refer [√]]))

(defn deep-merge
  "Recursively merge maps."
  [& maps]
  (letfn [(m [& nodes]
            (if (some #(map? %) nodes)
              (apply merge-with m nodes)
              (last nodes)))]
    (reduce m maps)))

(defn chord-of-circle
  "The chord length of a circle with s as its sagitta."
  [s r]
  (√ (* (- r (/ s 2)) (* 8 s))))
