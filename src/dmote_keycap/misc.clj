;;; General logic.

(ns dmote-keycap.misc)

(defn deep-merge
  "Recursively merge maps."
  [& maps]
  (letfn [(m [& nodes]
            (if (some #(map? %) nodes)
              (apply merge-with m nodes)
              (last nodes)))]
    (reduce m maps)))

