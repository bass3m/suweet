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

(defn default-score-fn
  "The default scoring function for a tweet. We calculate the score as
  follows (3 * favorite-count + retweet-count) / follower-count"
  [fav rt fol]
  (/ (+ rt (* 3 fav)) fol))

(defn default-sort-fn
  "Given an item in the twlist coll, return a sortable value."
  ([link] (default-sort-fn link default-score-fn))
  ([link score-fn] (apply score-fn ((juxt :rt-counts :fav-counts :follow-count) link))))

(defn top-list-tweets
  "Process twitter list and return the top tweets"
  ([tw-list] (top-list-tweets tw-list 10 default-sort-fn))
  ([tw-list top-n sort-fn] (map format-top-tweet
                                (take top-n (reverse (sort-by sort-fn (:links tw-list)))))))

(defn top-tweets
  "Process all of our lists for the top tweets in each list.
  We're given the location of the config file"
  [cfg-file]
  (map top-list-tweets (-> {:cfg-file cfg-file}
                           twum/merge-cfg
                           twum/read-tw-lists)))
