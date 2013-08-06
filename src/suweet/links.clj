(ns suweet.links
  (:import [org.apache.tika.parser ParseContext]
           [org.apache.tika.parser.html BoilerpipeContentHandler HtmlParser]
           [org.apache.tika.sax BodyContentHandler]
           [org.apache.tika.metadata Metadata]
           [de.l3s.boilerpipe.extractors ArticleExtractor])
  (:require [clojure.java.io :as io :only [input-stream]]
            [clojure.string :as s :only [trim replace]]))

(defn get-text-content
  "Use Apache Tika to retrieve content from the given url."
  [url]
  (with-open [i-stream (io/input-stream url)]
    (let [text (-> (BodyContentHandler. -1) BoilerpipeContentHandler.)
          metadata (Metadata.)
          parser (HtmlParser.)
          context (ParseContext.)]
      (.parse parser i-stream text metadata context)
      (.. text getTextDocument getContent))))

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
      (s/replace  #"\A[\s]+|[\s]+\Z|^[\s]$+" "")
      (s/replace #"[\s]{3,}" " ")
      s/trim))

(defn parse-url
  "Extracts main content text. By default, Article Extractor is used"
  [url]
  (let [html (slurp url)]
   (.getText (ArticleExtractor.) ^String html)))
