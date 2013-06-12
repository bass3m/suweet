(ns twum.summarize
  (:use [clojure.string :only (join)])
  (:use [opennlp.nlp :only (make-sentence-detector make-tokenizer)])
  (:use [stemmer.snowball]))

;; Based on Luhn '58 paper

; only english at the moment
(def get-sentences  (make-sentence-detector "models/en-sent.bin"))
(def tokenize       (make-tokenizer "models/en-token.bin"))
; stemmer see: http://snowball.tartarus.org/
(def en-stemmer     (stemmer "english"))

; find max score over the sentence within the cluster size.
; very basic ranking algorithm
(defn rank-sentence
  ([sentence significant cfg] (rank-sentence sentence (map key significant) cfg 0))
  ([sentence significant cfg score]
   (if (empty? sentence)
     score
     (recur (rest sentence) significant cfg
                  (max score
                       (reduce (fn [acc x]
                                 (if (some #(= x %) significant)
                                             (inc acc) acc))
                               0 (take (:word-cluster-size cfg) sentence)))))))

(defn filter-sentence
  "Given a sentence return tokenized sentence together with the
  indeces of word position in the sentence with the non-significant
  word filtered out."
  [sentence significant]
  (for [snt (map-indexed vector sentence)
        sig significant
        :when (= sig (second snt))] snt))

; (def sec-snt (second (map #(filter-sentence % (map key sigy)) sents)))
(defn sentence-rank
  "Calculate the sentence score. Find the maximum score for a segment
  of a sentence within :word-cluster-size words. We are passed words
  which are significant together with their location in the sentence.
  The score is calculated by squaring the num of significant word count
  divided by the total num of words in that segment.
  sentence: tokenized words in the sentence.
  sigificant: stemmed significant words along with their frequencies."
  [sentence significant cfg]
  (let [sentence (filter-sentence sentence (map key significant))]
    (:max-score 
      (reduce (fn [acc [n _]] 
                    (cond
                      (or (> n (+ (:word-cluster-size cfg) (:last acc)))
                          (zero? (:sig-size acc))) (assoc acc :start n :sig-size 1 :last n 
                                                          :max-score (max (:max-score acc) 1))
                      :else (let [new-sig (inc (:sig-size acc))
                                  size (+ 1 (- n (:start acc)))] 
                              (-> acc (assoc :sig-size new-sig)
                                  (assoc :max-score (max (:max-score acc)
                                                         (/ (* new-sig new-sig)
                                                            size)))
                                  (assoc :last n)))))
                  {:last 0 :start 0 :sig-size 0 :max-score 0} sentence))))

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
  (let [stop-words (into #{} (clojure.string/split-lines
                               (slurp (:stop-words cfg))))]
    (filter #(not (contains? stop-words %)) words)))

(defn significant-words
  "Given text return all the significant words. Which are the
  original text without all the stop words and after it's been
  run through the stemmer. Returns lazyseq of vectors containing
  map of word key and frequency as value."
  [text cfg]
  (->> text
       clojure.string/lower-case
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
                           :algorithm {:type "luhn" :params {:word-cluster-size 4}}
                           :word-cluster-size 4
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
                         clojure.string/lower-case
                         tokenize
                         stem-words ;; added
                         (sentence-rank significant cfg)))
          sentences-map))))



;(take (:num-sentences cfg) (reverse (sort-by )))

;; (take (Math/round (* 8 0.9)) (significant-words senty {:stop-words "models/english.txt"}))
;; Process words: find all words:
;; lowercase all , filter through stop-words,
;; use stemmer,
;; count words and sort (generate a freq dist) (take top 100 words)
;; this will provide the significant words
;; find sentences: lowercase as well
;; find sentence score.
;;
