;;; A CLI application for generating 3D models.

(ns dmote-keycap.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :refer [make-parents]]
            [environ.core :refer [env]]
            [scad-app.core :refer [build-all]]
            [dmote-keycap.models :as models])
  (:gen-class :main true))

(def cli-options
  "Define command-line interface flags."
  [["-V" "--version" "Print program version and exit"]
   ["-h" "--help" "Print this message and exit"]
   ["-r" "--render" "Render SCAD to STL"]
   [nil "--rendering-program PATH" "Path to OpenSCAD" :default "openscad"]])

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
           [{:name "smallflat",
             :model-main (models/smallflat-model options)}
            {:name "mediumflat",
             :model-main (models/mediumflat-model options)}]
           options)))))
