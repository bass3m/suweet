(ns twum.links
  (:import [org.apache.tika.parser Parser ParseContext]
           [org.apache.tika.parser.html HtmlParser]
           [org.apache.tika.sax BodyContentHandler] 
           [org.apache.tika.metadata Metadata])
  (:require [clojure.java.io :as io :only  [input-stream]]))

(defn clean-html
  "Remove html markup from a string."
  [html-str]
  )

(defn parse-url
  "Use Apache Tika to retrieve content from the given url."
  [url]
  (with-open [i-stream (io/input-stream url)]
    (let [text (BodyContentHandler.)
          metadata (Metadata.) 
          parser (HtmlParser.)
          context (ParseContext.)]
      (.parse parser i-stream text metadata context)
      (assoc {} :url url :text (.toString text)))))

