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

(defn top-list-tweets
  "Process twitter list and return the top tweets"
  ([tw-list] (top-list-tweets tw-list 10 default-score-fn))
  ([tw-list top-n score-fn] ()))

(defn top-tweets
  "Process all of our lists for the top tweets in each list.
  We're given the location of the config file"
  [cfg-file]
  ; read config file
  (map top-list-tweets (-> {:cfg-file cfg-file} read-cfg read-tw-lists))
  (read-tw-lists (:directory (read-cfg {:cfg-file cfg-file})))
  )

;; invoke (top-tweets "config.txt")

