;;; A CLI application for generating 3D models.

(ns dmote-keycap.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :refer [make-parents]]
            [environ.core :refer [env]]
            [scad-app.core :refer [build-all]]
            [scad-clj.model :refer [fs!]]
            [dmote-keycap.data :as data]
            [dmote-keycap.models :as models])
  (:gen-class :main true))

(def cli-options
  "Define command-line interface flags."
  [["-V" "--version" "Print program version and exit"]
   ["-h" "--help" "Print this message and exit"]
   ["-r" "--render" "Render SCAD to STL"]
   [nil "--rendering-program PATH" "Path to OpenSCAD" :default "openscad"]
   [nil "--sectioned" "Show models in section (cut in half)"]
   [nil "--face-size N" "Smaller number gives more detail; CLI default is 0.1"
    :default 0.1, :parse-fn #(Float/parseFloat %)]
   [nil "--switch-type TYPE" "One of “alps” (default) or “mx”"
    :parse-fn keyword, :validate [(set (keys data/switches))]]
   [nil "--body-style TYPE" "One of “minimal” (default) or “maquette”"
    :parse-fn keyword, :validate [data/body-styles]]
   [nil "--error-stem-positive N" "Printer error in mm; CLI default is 0"
    :default 0, :parse-fn #(Float/parseFloat %)]
   [nil "--error-stem-negative N" "Printer error in mm; CLI default is -0.15"
    :default -0.15, :parse-fn #(Float/parseFloat %)]])

(defn -main
  "Basic command-line interface logic."
  [& raw]
  (let [args (parse-opts raw cli-options)
        help-text (fn [] (println "dmote-keycap options:")
                         (println (:summary args)))
        version (fn [] (println "dmote-keycap version"
                         (env :dmote-keycap-version)))
        error (fn [] (run! println (:errors args)) (System/exit 1))]
   (cond
     (get-in args [:options :help]) (help-text)
     (get-in args [:options :version]) (version)
     (some? (:errors args)) (error)
     :else
       (let [options (:options args)]
         (build-all
           ;; An fs element is created here to control resolution, which is
           ;; of great practical importance for printing the corners.
           [{:name "cap", :model-vector [(fs! (:face-size options))
                                         (models/keycap options)]}]
           options)))))
