(ns twum.score
  (:require [clojure.string :as s :only [join]])
  (:require [twum.core :as twum :only [merge-cfg read-tw-lists]]))

(defn format-top-tweet
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
  (fn [cfg _ _ _]
    (:tw-score cfg)))

;; The default scoring function for a tweet. We calculate the score as
;; follows (3 * favorite-count + retweet-count) / follower-count"
(defmethod score-tweet :default
  [_ fav rt fol]
  (/ (+ rt (* 3 fav)) fol))

;; Define method for sorting the tweets
(defmulti sort-tweet
  (fn [cfg _]
    (:tw-sort cfg)))

;; the default option is to use a calculation based on
;; favorite counts, retweet counts and the number of followers
(defmethod sort-tweet :default
  [cfg link]
  (apply score-tweet cfg ((juxt :fav-counts :rt-counts :follow-count) link)))

(defn top-list-tweets
  "Process twitter list and return the top tweets"
  ([tw-list] (top-list-tweets {:top-tweets 10
                               :tw-sort :default
                               :tw-score :default} tw-list))
  ([cfg tw-list] (map format-top-tweet
                      (take (:top-tweets cfg) 
                            (reverse (sort-by (partial sort-tweet cfg) (:links tw-list)))))))

(defn top-tweets
  "Process all of our lists for the top tweets in each list.
  We're given the location of the config file"
  [cfg-file]
  (map (partial top-list-tweets cfg-file) (-> {:cfg-file cfg-file}
                                              twum/merge-cfg
                                              twum/read-tw-lists)))
