(ns twum.links
  (:import [org.apache.tika.parser Parser ParseContext]
           [org.apache.tika.parser.html HtmlParser]
           [org.apache.tika.sax BodyContentHandler] 
           [org.apache.tika.metadata Metadata])
  (:require [clojure.java.io :as io :only [input-stream]]
            [clojure.string :as s :only [trim replace]]))

(defn parse-url
  "Use Apache Tika to retrieve content from the given url."
  [url]
  (with-open [i-stream (io/input-stream url)]
    (let [text (BodyContentHandler. -1) ;; pass -1 to disable body size limit
          metadata (Metadata.) 
          parser (HtmlParser.)
          context (ParseContext.)]
      (.parse parser i-stream text metadata context)
      (assoc {} :url url :text (.toString text)))))

(defn clean-html
  "Remove html markup from a string and perform a little cleanup.
  Most of these patterns are from nltk."
  [html-str]
  (-> html-str
      s/trim
      (s/replace #"(?is)<(script|style).*?>.*?(</\1>)" "")
      (s/replace #"(?s)<!--(.*?)-->[\n]?" "")
      (s/replace #"(?s)<.*?>" " ")
      (s/replace #"&nbsp;" " ")
      (s/replace #"[\n]+|\s{2,}" " ")
      (s/replace #"\t" " . ")
      s/trim))
