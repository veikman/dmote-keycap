(defproject dmote-keycap "0.1.0-SNAPSHOT"
  :description "ALPS-compatible keycap models"
  :url "http://viktor.eikman.se/article/the-dmote/"
  :license {:name "EPL-2.0 OR GPL-3.0-or-later"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [environ "1.1.0"]
                 [scad-clj "0.5.3"]
                 [scad-tarmi "0.2.0"]]
  :plugins [[lein-environ "1.1.0"]]
  :main ^:skip-aot dmote-keycap.core)
