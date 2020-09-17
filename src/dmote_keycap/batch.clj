;;; Logic for batches, such as a complete set of keys for a keyboard.

(ns dmote-keycap.batch
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :refer [join split]]
            [dmote-keycap.schema :as schema]
            [dmote-keycap.models :as models]))

;;;;;;;;;;;;;;
;; Internal ;;
;;;;;;;;;;;;;;

(defn- compile-key-options
  "Compile options for the keycap function.
  If the raw entry is itself a long-form option map, fold it into the batch’s
  section options, else inject a short-form string into the option map."
  [{:keys [default-face-path] :as options
    :or {default-face-path [:legend :faces :top :char]}}
   raw]
  (let [entry (spec/conform ::schema/batch-entry raw)]
    (case (first entry)
      :short-form (compile-key-options options (assoc-in {} default-face-path raw))
      :long-form (merge-with merge options raw))))

(defn- section
  "Define scad-app assets for one section of a batch."
  [cli-options [section-options entries]]
  (map (partial compile-key-options (merge-with merge section-options cli-options))
       entries))

(defn- name-from-faces
  "String together a partial asset name from all possible legend strings.
  Filenames etc. in legend strings get no special treatment. This could change
  with a decent slugifier in a future version."
  [options]
  (->> (get-in options [:legend :faces] {})
    (vals)
    (mapcat (juxt :char :importable :unimportable))
    (remove nil?)
    (join "-")))

(defn- name-fn
  "Return a function to gives each asset a unique name."
  [n-places]
  (fn [index {:keys [filename] :as options}]
    (format (str "%s-%0" n-places "d-%s")
            filename index (name-from-faces options))))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn batch-assets
  "Define scad-app assets from the contents of an EDN file."
  [cli-options sections]
  (let [entries (remove nil? (mapcat (partial section cli-options) sections))
        namer (name-fn (-> entries count str count))]
    (map-indexed (fn [i entry]
                   {:name (namer i entry)
                    :model-main (models/keycap entry)
                    :minimum-face-size (:face-size entry)})
                 entries)))

