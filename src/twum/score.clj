(ns twum.score
  (:use [twum.core :only (read-cfg read-tw-lists)]))

; read config file, to find where we keep tweets
; then read tweets and do some basic sorting
;
; How we assign tweet scores:
; score = (3 * fav + rt) / num of followers from tweet


(defn default-score-fn
  "The default scoring function for a tweet. We calculate the score as
  follows (3 * favorite-count + retweet-count) / follower-count"
  [fav rt fol]
  (/ (+ rt (* 3 fav)) fol))
  ;(with-precision 3 (/ (+ rt (* 3M fav)) fol))  ; perhaps

(defn default-sort-fn
  "Given an item in the twlist coll, return a sortable value."
  ([link] (default-sort-fn link default-score-fn))
  ([link score-fn] (apply score-fn ((juxt :rt-counts :fav-counts :follow-count) link))))

; (def my-tw (read-tw-lists cfg))
; (def tech-tw (last my-tw))
; use sort-by function perhaps
; (map #((juxt :rt-counts :fav-counts :follow-count) %) (:links tech-tw))
; (map (fn [[n d]] (with-precision 3 (/ n d))) [[7 24088M]  [123 1024000M]])
(defn top-list-tweets
  "Process twitter list and return the top tweets"
  ([tw-list] (top-list-tweets tw-list 10 default-sort-fn))
  ([tw-list top-n sort-fn] (take top-n (sort-by sort-fn (:links tw-list)))))

(defn top-tweets
  "Process all of our lists for the top tweets in each list.
  We're given the location of the config file"
  [cfg-file]
  ; read config file
  (map top-list-tweets (-> {:cfg-file cfg-file} read-cfg read-tw-lists))
  ;(read-tw-lists (:directory (read-cfg {:cfg-file cfg-file})))
  )

;; invoke (top-tweets "config.txt")

