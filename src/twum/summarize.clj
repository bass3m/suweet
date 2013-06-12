(ns twum.summarize
  (:require [clojure.string :as string :only [lower-case split-lines]])
  (:require [opennlp.nlp :as nlp :only [make-sentence-detector make-tokenizer]])
  (:require [stemmer.snowball :as snowball]))

;; Based on Luhn '58 paper

; only english at the moment
(def get-sentences  (nlp/make-sentence-detector "models/en-sent.bin"))
(def tokenize       (nlp/make-tokenizer "models/en-token.bin"))
; stemmer see: http://snowball.tartarus.org/
(def en-stemmer     (snowball/stemmer "english"))

;; could also go with the slightly less readable 
;; ((comp :type :algorithm) cfg)
(defmulti rank-sentence 
  (fn [_ _ cfg] 
    (get-in cfg [:algorithm :type])))

; find max score over the sentence within the cluster size.
; very basic and probably dumb ranking algorithm
(defmethod rank-sentence :basic
  ([sentence significant cfg] 
   (rank-sentence sentence (map key significant) 
                  (get-in cfg [:algorithm :params :word-cluster-size]) 0))
  ([sentence significant word-cluster-size score]
   (if (empty? sentence)
     score
     (recur (rest sentence) significant word-cluster-size
                  (max score
                       (reduce (fn [acc x]
                                 (if (some #(= x %) significant)
                                             (inc acc) acc))
                               0 (take word-cluster-size sentence)))))))

(defn filter-sentence
  "Given a sentence return tokenized sentence together with the
  indeces of word position in the sentence with the non-significant
  word filtered out."
  [sentence significant]
  (for [snt (map-indexed vector sentence)
        sig significant
        :when (= sig (second snt))] snt))

;; Calculate the sentence score. Find the maximum score for a segment
;; of a sentence within :word-cluster-size words. We are passed words
;; which are significant together with their location in the sentence.
;; The score is calculated by squaring the num of significant word count
;; divided by the total num of words in that segment.
;; sentence: tokenized words in the sentence.
;; sigificant: stemmed significant words along with their frequencies. 
(defmethod rank-sentence :luhn
  [sentence significant cfg]
  (let [sentence (filter-sentence sentence (map key significant))
        word-cluster-size (get-in cfg [:algorithm :params :word-cluster-size])]
    (:max-score 
      (reduce (fn [acc [n _]] 
                (cond
                  (or (> n (+ word-cluster-size (:last acc)))
                      (zero? (:sig-size acc))) 
                    (assoc acc :start n 
                         :sig-size 1 :last n 
                         :max-score (max (:max-score acc) 1))
                  :else (let [new-sig (inc (:sig-size acc))
                              size (+ 1 (- n (:start acc)))] 
                          (-> acc (assoc :sig-size new-sig)
                              (assoc :max-score (max (:max-score acc)
                                                     (/ (* new-sig new-sig)
                                                        size)))
                              (assoc :last n)))))
              {:last 0 :start 0 :sig-size 0 :max-score 0} sentence))))

(defmethod rank-sentence :default
  [sentence significant cfg]
  (do (println "Not implemented yet: Sentence" sentence)
      (println "Config: " cfg)))

(defn filter-non-words
  "Given a coll of strings, get rid of non-words, periods, commas for example."
  [str-coll]
  (filter (fn [s] (every? #(Character/isLetterOrDigit %) s)) str-coll))

(defn stem-words
  "Given a coll of words, get the stems."
  [words]
  (map en-stemmer words))

(defn filter-stop-words
  "Get rid of stop words from a given coll of words"
  [cfg words]
  (let [stop-words (into #{} (string/split-lines
                               (slurp (:stop-words cfg))))]
    (filter #(not (contains? stop-words %)) words)))

(defn significant-words
  "Given text return all the significant words. Which are the
  original text without all the stop words and after it's been
  run through the stemmer. Returns lazyseq of vectors containing
  map of word key and frequency as value."
  [text cfg]
  (->> text
       string/lower-case
       tokenize
       filter-non-words
       (filter-stop-words cfg)
       stem-words
       frequencies
       (sort-by val)
       reverse))

(defn summarize
  "Attempt to summarize a given text into it's most relevant sentences"
  ([text] (summarize text {:num-sentences 3
                           :algorithm {:type :luhn :params {:word-cluster-size 4}}
                           :top-n-pct 0.9 ; XXX should be a freq distrib
                           :stop-words "models/english.txt"}))
  ([text cfg]
   (let [sentences (get-sentences text)
         sentences-map (map (fn [[k v]]
                              (assoc {} :order k :sentence v :score 0))
                            (zipmap (range (count sentences)) sentences))
         significant (significant-words text cfg)]
     (map #(assoc-in % [:score]
                     (-> (:sentence %)
                         string/lower-case
                         tokenize
                         stem-words ;; added
                         (rank-sentence significant cfg)))
          sentences-map))))
;
;(take (:num-sentences cfg) (reverse (sort-by :score (summarize text)))
; then sort by actual order.
;; (take (Math/round (* 8 0.9)) (significant-words senty {:stop-words "models/english.txt"}))
