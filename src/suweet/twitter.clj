(ns suweet.twitter
  (:require
    [twitter.api.restful :as twitter]
    [suweet.cfg :as cfg :only [my-creds]]))

(defn get-twitter-lists
  "Query twitter for our lists. Returns vector of maps."
  []
  (:body (twitter/lists-list :oauth-creds cfg/my-creds)))

(defn- get-expanded-urls-from-tw [tweets]
  (map (comp :expanded_url first :urls :entities) tweets))

(defn- save-latest-tweet-id [tw-list tweets]
  (update-in tw-list [:since-id] #(apply max (flatten [% (map :id tweets)]))))

(defn- get-oldest-tweet-id [tweets]
  (apply min (map :id tweets)))

(defn- process-tweet
  "Process an individual tweet. We only care about urls(contained in
  entities),text, fav count and rt count."
  ; if it's a new link then create a new link
  ; otherwise update the existing entry: by increasing count and adding
  ; another urlers to the set, also text and update the last activity
  [{:keys [links] :as tw-list}
   {:keys [text favorite_count retweet_count entities user id]}]
   (if-let [link (seq (for [my-link (seq links)
                            tw-url (map :expanded_url (:urls entities))
                            :when (and (not (nil? (:url my-link)))
                                       (= (:url my-link) tw-url))] my-link))]
     (assoc-in tw-list [:links (.indexOf links (first link))]
               (-> (first link)
                   (update-in [:count] inc)
                   (update-in [:text] conj text)
                   (update-in [:urlers] conj (:name user))
                   (update-in [:follow-count] max (:followers_count user))
                   (update-in [:rt-counts] max retweet_count)
                   (update-in [:fav-counts] max favorite_count)))
     (update-in tw-list [:links] conj
                {:url (:expanded_url (first (:urls entities)))
                 :count 1
                 :rt-counts retweet_count
                 :fav-counts favorite_count
                 :urlers (hash-set (:name user))
                 :follow-count (:followers_count user)
                 :last-activity (java.util.Date.)
                 :text (hash-set text)
                 :id id
                 :profile-image-url (:profile_image_url user)})))

(defn get-tweets
  "Get new tweets for the given twitter list.
  If since-id was set then we get tweets using since-id
  for the next twitter request we set the max-id to be the minimum
  tweet id that was returned from twitter - 1, this will get more tweets
  with a maximum id of the max-id. Twitter returns the most recent tweets
  so we have to walk the list backwards while merging the tweets
  into one big vector."
  [twitter-params]
  (let [get-list-tweets (partial twitter/lists-statuses
                                 :oauth-creds cfg/my-creds)]
    (loop [tweets (:body (get-list-tweets :params twitter-params))
           all-tweets []]
      (cond
        (empty? tweets) all-tweets
        (nil? (:since-id twitter-params)) tweets
        :else (recur (:body (get-list-tweets
                              :params
                              (-> twitter-params
                                  (merge {:max-id
                                          (- (apply min (map :id tweets)) 1)}))))
                     (reduce conj all-tweets tweets))))))

(defn- process-list-tweets
  "Call twitter api for a list and process the tweets"
  [tw-list twitter-params]
  (let [tweets (get-tweets twitter-params)]
    (reduce #(process-tweet %1 %2)
            (save-latest-tweet-id tw-list tweets)
            tweets)))

(defn get-twitter-list-tweets
  "Call twitter api for a list. Get 50 tweets/call"
  [tw-list]
  (let [twitter-params {:list-id (:list-id tw-list) :count 50}
        since-id (:since-id tw-list)]
    (cond
      (zero? since-id) (process-list-tweets tw-list twitter-params)
      :else (process-list-tweets tw-list (merge twitter-params {:since-id since-id})))))
