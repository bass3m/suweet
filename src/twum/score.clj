(ns twum.score
  (:require [clojure.string :as s :only [join]]))

(defn format-tweet
  "Format a top tweet in a more user friendly view"
  [tw]
  (format "\nBy: %s\n%s\n%sReTweeted by: %d Favorited by: %d Followers: %d\n"
          (s/join ":" (concat (:urlers tw)))
          (s/join ":" (concat (:text tw)))
          (if (not (nil? (:url tw))) (s/join ["Link: " (:url tw) "\n"]) "")
          (:rt-counts tw) (:fav-counts tw) (:follow-count tw)))

(defmulti score-tweet
  "Scoring function for a tweet. The idea is to be able to vary the
  method used to calculate the score of a tweet"
  (fn [cfg rest-args]
    (:tw-score cfg)))

;; The default scoring function for a tweet. We calculate the score as
;; follows (3 * favorite-count + retweet-count) / follower-count"
(defmethod score-tweet :default
  [_ [fav rt fol]]
  (/ (+ rt (* 3 fav)) fol))

;; Define method for sorting the tweets
(defmulti sort-tweet
  (fn [cfg _]
    (:tw-sort cfg)))

;; the default option is to use a calculation based on
;; favorite,  retweet counts and the number of followers
(defmethod sort-tweet :default
  [cfg link]
  (score-tweet cfg ((juxt :fav-counts :rt-counts :follow-count) link)))

(defn top-tw-list
  "Process twitter list and return the top tweets"
  ([tw-list] (top-tw-list {:top-tweets 10
                           :tw-sort  :default
                           :tw-score :default} tw-list))
  ([cfg tw-list] (assoc tw-list :links 
                           (take (:top-tweets cfg)
                                 (reverse (sort-by (partial sort-tweet cfg)
                                                   (:links tw-list)))))))
(defn format-top-tweets
  "Format top twitter list tweets to be ready to print"
  ([tw-list] (format-top-tweets {:top-tweets 10
                                 :tw-sort  :default
                                 :tw-score :default} tw-list))
  ([cfg tw-list] (map format-tweet (top-tw-list cfg tw-list))))
