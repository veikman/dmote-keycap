;;; Logic for batches, such as a complete set of keys for a keyboard.

(ns dmote-keycap.batch
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :refer [join split]]
            [me.raynes.fs :as fs]
            [dmote-keycap.data :refer [face-keys]]
            [dmote-keycap.misc :refer [deep-merge]]
            [dmote-keycap.models :as models]
            [dmote-keycap.schema :as schema]))

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
      :long-form (deep-merge options raw))))

(defn- section
  "Define scad-app assets for one section of a batch."
  [cli-options [section-options entries]]
  (map (partial compile-key-options (deep-merge section-options cli-options))
       entries))

(defn- name-from-faces
  "String together a partial asset name from all possible legend strings.
  Filenames etc. in legend strings get no special treatment. This could change
  with a decent slugifier in a future version."
  [options]
  (let [face-index (fn [[face-key _]] (.indexOf face-keys face-key))]
    (->> (get-in options [:legend :faces] {})
      (sort-by face-index)
      (map second)
      (mapcat (juxt :char :importable :unimportable))
      (remove nil?)
      (map #(fs/base-name % true))
      (join "_"))))

(defn- name-fn
  "Return a function to gives each asset a unique name."
  [n-places]
  (fn [index {:keys [filename legend] :as options}]
    (let [suffix (:filename-suffix legend)]
      (format (str "%s-%0" n-places "d_%s")
              filename index (or suffix (name-from-faces options))))))

(defn- define-asset
  "Define one scad-app asset.
  Make sure each SCAD output will have a unique name, and matching names for
  each of its SVG files for legends."
  [namer i entry]
  (let [filename (namer i entry)]
    {:name filename
     :model-main (models/keycap (assoc entry :filename filename))
     :minimum-facet-size (:facet-size entry)
     :minimum-facet-angle (:facet-angle entry)}))


;;;;;;;;;;;;;;;
;; Interface ;;
;;;;;;;;;;;;;;;

(defn batch-assets
  "Define scad-app assets from the parsed contents of an EDN file."
  [cli-options sections]
  (let [entries (remove nil? (mapcat (partial section cli-options) sections))
        namer (name-fn (-> entries count str count))]
    (map-indexed (partial define-asset namer) entries)))

