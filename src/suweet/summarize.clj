(ns twum.summarize
  (:require [clojure.string :as s :only [lower-case split-lines]])
  (:require [opennlp.nlp :as nlp :only [make-sentence-detector make-tokenizer]])
  (:require [stemmer.snowball :as snowball]))

;; Based on Luhn '58 paper

; only english at the moment
(def get-sentences  (nlp/make-sentence-detector "models/en-sent.bin"))
(def tokenize       (nlp/make-tokenizer "models/en-token.bin"))
; stemmer see: http://snowball.tartarus.org/
(def en-stemmer     (snowball/stemmer "english"))

;; could also go with the slightly less readable
;; ((comp :type :algo) cfg)
(defmulti rank-sentence
  (fn [_ _ cfg]
    (get-in cfg [:algo :type])))

; find max score over the sentence within the cluster size.
; very basic and probably dumb ranking algorithm
(defmethod rank-sentence :basic
  ([sentence significant cfg]
   (rank-sentence sentence (map key significant)
                  (get-in cfg [:algo :params :word-cluster-size]) 0))
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
        word-cluster-size (get-in cfg [:algo :params :word-cluster-size])]
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
  (do (println "Not implemented yet")
      (println "Config: " cfg "what:" (get-in cfg [:algo :type]))))


(defn filter-string-non-words
  "Given a string, filter the non Char or Digits."
  [st]
  (s/join (filter #(Character/isLetterOrDigit %) st)))

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
  (let [stop-words (into #{} (s/split-lines
                               (slurp (:stop-words cfg))))]
    (filter #(not (contains? stop-words %)) words)))

;; could use incanter, but this is simple enough
(defn mean-frequencies
  "Given a coll from output of frequencies, get the mean.
  Coll is an ArraySeq containing MapEntry's with key as item
  and val as the frequency."
  [freq-coll]
  (let [sum (apply + (map (fn [[x y]] (* x y)) freq-coll))
        times (apply + (map key freq-coll))]
    (with-precision 3 (/ sum times))))

(defn mean
  [coll]
  (/ (apply + coll) (count coll)))

(defn std
  "Calculate the standard deviation"
  [coll]
  (let [mean (mean coll)
        sqr #(* % %)]
    (Math/sqrt (/ (apply + (map #(sqr (- % mean)) coll)) (count coll)))))

;; http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance
;; Source in C++ :  http://www.johndcook.com/standard_deviation.html
;; From Knuth TAOCP vol 2, 3rd edition, page 232
(defn mean-variance
  [coll]
  (reduce (fn [{:keys [n mean m2] :as acc} new-n] 
            (let [new-mean (+ mean  (/ (- new-n mean) (inc n)))
                  new-m2 (+ m2 (* (- new-n mean) (- new-n new-mean)))]
              (-> acc
                  (assoc :n (inc n))
                  (assoc :mean new-mean)
                  (assoc :m2 new-m2)))) 
          {:n 0 :mean 0 :m2 0} coll))

(defn online-mean
  [coll]
  (:mean (mean-variance coll)))

(defn online-std
  [coll]
  (let [{:keys [n m2]} (mean-variance coll)]
    (Math/sqrt (/ m2 n))))

; :score-algo {:type :top-n :params {:val 100}}
; :score-algo {:type :pct :params {:val 0.9}}
; :score-algo {:type :freq :params {:freq-factor 0.5}}
(defmulti filter-low-score-sentences
  (fn [cfg _]
    ((comp :type :score-algo) cfg)))

(defmethod filter-low-score-sentences :freq
  [cfg sents]
  (let [coll-scores (map :score sents)
        score-threshold (+ (mean coll-scores) 
                           (* (std coll-scores) 
                              ((comp :freq-factor :params :score-algo) cfg)))]
    (filter #(> (:score %) score-threshold) sents)))

;; sents should be reverse sorted
(defmethod filter-low-score-sentences :pct
  [cfg sents]
  (let [pct ((comp :val :params :score-algo) cfg)]
    (if (< 0 pct 1)
      (take (Math/round (* (count sents) pct)) sents))))

;; sents should be reverse sorted, we want to keep top-n
;; number of sentences
(defmethod filter-low-score-sentences :top-n
  [cfg sents]
  (take ((comp :val :params :score-algo) cfg) sents))

(defmethod filter-low-score-sentences :default
  [cfg sents]
  (println "Not implemented. Config: " cfg))

(defn significant-words
  "Given text return all the significant words. Which are the
  original text without all the stop words and after it's been
  run through the stemmer. Returns lazyseq of vectors containing
  map of word key and frequency as value."
  [text cfg]
  (->> text
       s/lower-case
       tokenize
       filter-non-words
       (filter-stop-words cfg)
       stem-words
       frequencies
       (sort-by val)
       reverse))

;(defn min-sentences
  ;"Get the minimum number of sentences to return.
  ;If squareroot of num of sentences is less than 3, then return 3.
  ;Otherwise return the squaroot of num of sentences.")

(defn calc-sentences
  "Calculate the significance factor (score) for all sentences in text."
  [text cfg]
  (let [sentences (get-sentences text)
        sentences-map (map (fn [[k v]]
                             (assoc {} :order k :sentence v :score 0))
                           (zipmap (range (count sentences)) sentences))
        significant (significant-words text cfg)]
    (map #(assoc-in % [:score]
                    (-> (:sentence %)
                        s/lower-case
                        tokenize
                        stem-words
                        (rank-sentence significant cfg)))
         sentences-map)))

(defn format-summary
  "Make summary more viewable"
  [tweet-summary]
  (map (comp  print :summary) tweet-summary))

(defn summarize
  "Attempt to summarize a given text into it's most relevant sentences"
  ([text] (summarize text {:num-sentences 3
                           :algo {:type :luhn :params {:word-cluster-size 4}}
                           :score-algo {:type :freq :params {:freq-factor 0.5}}
                           :stop-words "models/english.txt"}))
  ([text cfg] (vec (take (:num-sentences cfg) (->> cfg
                                                   (calc-sentences text)
                                                   (sort-by :score)
                                                   reverse
                                                   (filter-low-score-sentences cfg)
                                                   (sort-by :order))))))
