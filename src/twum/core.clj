(ns twum.core
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.restful]
   [twum.cfg :only (my-creds)]))
  ;(:import
   ;(twitter.callbacks.protocols SyncSingleCallback)))

;(defrecord Twum    [invoked last-update cfg])
(defrecord Tw-list [list-name list-id since-id links])
(defrecord Tw-url  [url count rt-counts fav-counts
                    urlers last-activity text])

;(defn new-twum [cfg]
  ;(map->Twum {:invoked 1 
              ;:last-update (str (java.util.Date.))
              ;:cfg cfg}))

(defn new-tw-url []
  (map->Tw-url {:url ""
                :count 0
                :rt-counts 0
                :fav-counts 0
                :urlers #{}
                :last-activity ""
                :text #{}}))

(defn new-tw-list []
  (map->Tw-list {:list-name ""
                 :list-id ""
                 :since-id 0
                 ; links will contain a multitude of Tw-link
                 :links []}))


(defn get-twitter-lists [] 
  (:body  (twitter.api.restful/lists-list :oauth-creds my-creds)))


(defn get-new-tw-lists
  "Call the get lists/list twitter api to get our lists"
  []
  (for [{:keys [slug id]} (get-twitter-lists)] 
    [(assoc (new-tw-list) :list-name slug :list-id id)]))

(defn get-expanded-urls-from-tw [tweets]
  (map (comp :expanded_url first :urls :entities) tweets))

(defn get-tweet-ids [tweets]
  (map :id tweets))

(defn get-latest-tweet-id [tw-list tweets]
  (update-in tw-list [:since-id] #(apply max (flatten [% (map :id tweets)]))))

(defn process-tweet
  "Process an individual tweet. We only care about urls(contained in
  entities),text, fav count and rt count."
  ; if it's a new link then create a new Tw-link
  ; otherwise update the existing entry: by increasing count and adding
  ; another urlers to the set, also text and update the last activity
  [{:keys [links] :as tw-list}
   {:keys [text favorite_count retweet_count entities user]}]
   (if-let [link (seq (for [my-link (seq links)
                            tw-url (map :expanded_url (:urls entities))
                            :when (= (:url my-link) tw-url)] my-link))]
     (update-in tw-list [:links] conj
                (-> link
                  (update-in [:count] inc)
                  (update-in [:text] conj text)
                  (update-in [:urlers] conj (:name user))
                  (update-in [:rt-counts] max retweet_count)
                  (update-in [:fav-counts] max favorite_count)))
     ; otherwise, create a new entry
     (update-in tw-list [:links] conj
                 (map->Tw-url {:url (:expanded_url (first (:urls entities)))
                               :count 1 :rt-counts retweet_count
                               :fav-counts favorite_count
                               :urlers (hash-set (:name user))
                               :last-activity (str (java.util.Date.))
                               :text (hash-set text)}))))

(defn process-list-tweets
  "Call twitter api for a list and process the tweets"
  [tw-list twitter-params]
  (let [tweets (:body (twitter.api.restful/lists-statuses
                        :oauth-creds my-creds
                        :params twitter-params))
        url-summaries (reduce #(process-tweet %1 %2) 
                              (get-latest-tweet-id tw-list tweets)
                              tweets)]
    (spit (:list-name tw-list) url-summaries)))

(defn get-twitter-list-tweets
  "Call twitter api for a list"
  [tw-list]
  (let [tw-list (first tw-list)
        twitter-params {:list-id (:list-id tw-list)}]
  (cond
    (zero? (:since-id tw-list)) (process-list-tweets tw-list twitter-params)
    :else (process-list-tweets tw-list (merge twitter-params {:since-id (:since-id tw-list)})))))

(defn read-list-tweets
  "Query twitter for tweets for each of our lists"
  [tw-lists]
  (map get-twitter-list-tweets tw-lists))

(defn get-new-tw-lists
  "Call the get lists/list twitter api to get our lists"
  [cfg]
  (for [{:keys [slug id]} (get-twitter-lists)]
    [(assoc (new-tw-list) 
            :list-name (clojure.string/join [(:directory cfg) "/" slug]) 
            :list-id id)]))

(defn update-tw-links
  "Start checking twitter timeline for any url and merge if needed.
  If the given twitter list is empty then check twitter for our lists
  and create a new one."
  [tw-lists cfg] ; i hate this form/pattern need to clean that up
  (if (nil? tw-lists)
    (read-list-tweets (get-new-tw-lists cfg))
    (read-list-tweets tw-lists)))

(defn read-tw-list [tw-lists file]
  (let [tw-list (read-string (slurp (.getAbsolutePath file)))]
    (vector tw-lists tw-list)))

(defn read-tw-lists-hist
  "We're passed the tw list history dir (containing were we left off).
  1 file per twitter list we're tracking."
  [tw-lists dir]
  (if (.exists (java.io.File. dir)) 
    (reduce read-tw-list tw-lists
           (.listFiles (java.io.File. dir)))
    (do (.mkdirs (java.io.File. dir))
        nil)))

(defn read-tw-cfg
  "look through lists dir and load twitter list history in map"
  [tw-list cfg]
  (read-tw-lists-hist tw-list (:directory cfg)))

; for cli args : https://github.com/clojure/tools.cli
(defn -main
  ([cfg] (update-tw-links (read-tw-cfg (new-tw-list) cfg) cfg))
  ([] (-main {:directory "twlist"})))

