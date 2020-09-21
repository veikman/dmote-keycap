;;; Input schema.

(ns dmote-keycap.schema
  (:require [clojure.spec.alpha :as spec]
            [clojure.java.io :as io]
            [scad-tarmi.core :as tarmi]
            [dmote-keycap.data :refer [switches]]))

;;;;;;;;;;;;;
;; Parsers ;;
;;;;;;;;;;;;;

(defn map-like
  "Return a parser of a map where the exact keys are known."
  [key-value-parsers]
  (letfn [(parse-item [[key value]]
            (if-let [value-parser (get key-value-parsers key)]
              [key (value-parser value)]
              (throw (Exception. (format "Invalid key: %s" key)))))]
    (fn [candidate] (into {} (map parse-item candidate)))))

(defn map-of
  "Return a parser of a map where the general type of key is known."
  [key-parser value-parser]
  (letfn [(parse-item [[key value]]
            [(key-parser key) (value-parser value)])]
    (fn [candidate] (into {} (map parse-item candidate)))))

;; Crude parsers for configuration deserialization in applications.
;; Notice these are not used in core.clj, which needs heavier-duty parsers
;; for working with strings from a CLI.
;; Notice also that functions passable as arguments are omitted because they
;; would not normally be recoverable from serialized data.
(def option-parsers {:filename str
                     :style keyword
                     :switch-type keyword
                     :unit-size vec
                     :top-size vec
                     :top-rotation vec
                     :bowl-radii vec
                     :bowl-plate-offset num
                     :skirt-length num
                     :skirt-thickness num
                     :slope num
                     :legend (map-like
                               {:depth num
                                :faces (map-of keyword
                                         (map-like
                                           {:unimportable str
                                            :importable str
                                            :char str
                                            :style (map-of keyword
                                                              identity)}))})
                     :nozzle-width num
                     :horizontal-support-height num
                     :error-body-positive num
                     :error-stem-positive num
                     :error-stem-negative num
                     :sectioned boolean
                     :supported boolean})


;;;;;;;;;;;;;;;;;;;;;;;;;
;; Predicate functions ;;
;;;;;;;;;;;;;;;;;;;;;;;;;

(defn file?
  [filepath]
  (let [file (io/file filepath)]
    (and (-> file .exists)
         (not (-> file .isDirectory)))))


;;;;;;;;;;;;;;;;;;;;;;;
;; Spec registration ;;
;;;;;;;;;;;;;;;;;;;;;;;

(spec/def ::importable-filepath-fn fn?)

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

(spec/def ::depth (spec/and number? #(>= % 0)))
(spec/def ::face-id #{:top :north :east :south :west})
(spec/def ::unimportable (spec/and string? file?))
(spec/def ::importable (spec/and string? (complement empty?)))
(spec/def ::char (spec/and string? (complement empty?)))
(spec/def ::style map?)
(spec/def ::face-data (spec/keys :opt-un
                        [::unimportable ::importable ::char ::style]))
(spec/def ::faces (spec/map-of ::face-id ::face-data))
(spec/def ::legend (spec/keys :opt-un [::depth ::faces]))

(spec/def ::nozzle-width (spec/and number? #(> % 0)))
(spec/def ::horizontal-support-height (spec/and number? #(>= % 0)))
(spec/def ::error-stem-positive number?)
(spec/def ::error-stem-negative number?)
(spec/def ::error-body-positive number?)
(spec/def ::sectioned boolean?)
(spec/def ::supported boolean?)

;; A composite spec for all valid parameters going into the keycap model.
(spec/def ::keycap-parameters
  (spec/keys :opt-un [::importable-filepath-fn
                      ::style ::switch-type ::unit-size
                      ::top-size ::top-rotation
                      ::bowl-radii ::bowl-plate-offset
                      ::skirt-thickness ::skirt-length ::slope
                      ::legend
                      ::nozzle-width ::horizontal-support-height
                      ::error-stem-positive ::error-stem-negative
                      ::error-body-positive ::sectioned ::supported]))

;; Composite specs for batch mode.
(spec/def ::batch-entry
  (spec/or :short-form string?
           :long-form ::keycap-parameters))
(spec/def ::batch-file
  (spec/map-of ::keycap-parameters (spec/coll-of ::batch-entry)))
