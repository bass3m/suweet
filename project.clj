(defproject twum "0.1.0-SNAPSHOT"
  :description "Utility for summarizing Twitter lists"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :main twum.core
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.slf4j/slf4j-simple "1.6.2"]
                 [clj-time "0.5.1"]
                 [twitter-api "0.7.4"]
                 [org.apache.tika/tika-parsers "1.3"]
                 [org.clojars.gnarmis/snowball-stemmer "0.1.1-SNAPSHOT"]
                 [clojure-opennlp "0.3.0"]])
