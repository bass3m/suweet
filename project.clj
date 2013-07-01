(defproject suweet "0.1.1"
  :description "Utility for summarizing Twitter lists"
  :url "https://github.com/bass3m/suweet.git"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main suweet.core
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.slf4j/slf4j-simple "1.6.2"]
                 [clj-time "0.5.1"]
                 [org.clojure/tools.cli "0.2.2"]
                 [twitter-api "0.7.4"]
                 [org.apache.tika/tika-parsers "1.3"]
                 [org.clojars.gnarmis/snowball-stemmer "0.1.1-SNAPSHOT"]
                 [clojure-opennlp "0.3.0"]])
