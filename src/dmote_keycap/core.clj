;;; A CLI application for generating 3D models.

(ns dmote-keycap.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.shell :refer [sh]]
            [clojure.java.io :refer [make-parents]]
            [environ.core :refer [env]]
            [scad-clj.scad :refer [write-scad]]
            [dmote-keycap.models :as models])
  (:gen-class :main true))

(def basenames-to-functions
  "Application-specific pairs of filenames and producing functions."
  {"smallflat" models/smallflat-model
   "mediumflat" models/mediumflat-model})

(defn- render-to-stl
  "Call OpenSCAD to render an SCAD file to STL."
  [renderer path-scad path-stl]
  (make-parents path-stl)
  (if (zero? (:exit (sh renderer "-o" path-stl path-scad)))
    (println "Rendered" path-stl)
    (do
      (println "Rendering failed")
      (System/exit 1))))

(defn- write-all
  "Author SCAD files, one per model."
  [{:keys [render renderer whitelist] :as options}]
  (letfn [(file-output [[basename producer]]
            (let [scad (str "output/scad/" basename ".scad")
                  stl (str "output/stl/" basename ".stl")]
             (if (nil? (re-find whitelist basename))
               (println "Skipping" scad "(not whitelisted)")
               (do
                 (println "Transpiling" scad)
                 (make-parents scad)
                 (spit scad (write-scad (producer options)))
                 (if render (render-to-stl renderer scad stl))))))]
   (doall (pmap file-output basenames-to-functions))))

(def cli-options
  "Define command-line interface flags."
  [["-V" "--version" "Print program version and exit"]
   ["-h" "--help" "Print this message and exit"]
   ["-r" "--render" "Render SCAD to STL"]
   [nil "--renderer PATH" "Path to OpenSCAD" :default "openscad"]
   ["-w" "--whitelist RE"
    "Limit output to files whose names match the regular expression RE"
    :default #"" :parse-fn re-pattern]])

(defn -main
  "Basic command-line interface logic."
  [& raw]
  (let [args (parse-opts raw cli-options)
        help-text (fn [] (do (println "dmote-keycap options:")
                             (println (:summary args))))
        version (fn [] (println "dmote-keycap version" (env :dmote-keycap-version)))
        error (fn [] (do (run! println (:errors args))
                         (System/exit 1)))]
   (cond
     (get-in args [:options :help]) (help-text)
     (get-in args [:options :version]) (version)
     (some? (:errors args)) (error)
     :else (write-all (:options args))))
  (System/exit 0))
