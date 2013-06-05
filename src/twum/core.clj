(ns twum.core
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.restful]
   [clj-time.core]
   [twum.cfg :only (my-creds)]))


(defrecord Tw-list [list-name list-id since-id links])
(defrecord Tw-url  [url count rt-counts fav-counts
                    urlers last-activity text])

(defn new-tw-list []
  (map->Tw-list {:list-name ""
                 :list-id ""
                 :since-id 0
                 ; links will contain a multitude of Tw-link
                 :links []}))

(defn get-twitter-lists 
  "Query twitter for our lists. Returns vector of maps."
  []
  (:body  (twitter.api.restful/lists-list :oauth-creds my-creds)))

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
  ; XXX filter out tweets with no urls, perhaps not needed.
  [{:keys [links] :as tw-list}
   {:keys [text favorite_count retweet_count entities user]}]
   (if-let [link (seq (for [my-link (seq links)
                            tw-url (map :expanded_url (:urls entities))
                            :when (= (:url my-link) tw-url)] my-link))]
     (assoc-in tw-list [:links (.indexOf links (first link))]
               (-> (first link)
                   (update-in [:count] inc)
                   (update-in [:text] conj text)
                   (update-in [:urlers] conj (:name user))
                   (update-in [:rt-counts] max retweet_count)
                   (update-in [:fav-counts] max favorite_count)))
     (update-in tw-list [:links] conj
                 (map->Tw-url {:url (:expanded_url (first (:urls entities)))
                               :count 1 :rt-counts retweet_count
                               :fav-counts favorite_count
                               :urlers (hash-set (:name user))
                               :last-activity (java.util.Date.)
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
    (do (spit (:list-name tw-list) (pr-str url-summaries))
        url-summaries)))

(defn get-twitter-list-tweets
  "Call twitter api for a list"
  [tw-list]
  (let [twitter-params {:list-id (:list-id tw-list)}
        since-id (:since-id tw-list)]
  (cond
    (zero? since-id) (process-list-tweets tw-list twitter-params)
    :else (process-list-tweets tw-list (merge twitter-params {:since-id since-id})))))

(defn read-list-tweets
  "Query twitter for tweets for each of our lists"
  [tw-lists]
  (map get-twitter-list-tweets tw-lists))

(defn get-new-tw-lists
  "Call the get lists/list twitter api to get our lists. 
  If the tw-lists-to-track is empty that implies that we want
  to monitor all our lists. If not, then monitor only the list
  listed in that set."
  [cfg]
  (for [{:keys [slug id]} (get-twitter-lists)
        :let [tw-list (:tw-lists-to-track cfg)] 
        :when (or (empty? tw-list)
                  (contains? tw-list slug))]
    [(assoc (new-tw-list)
            :list-name (clojure.string/join [(:directory cfg) "/" slug])
            :list-id id)]))

(defn update-tw-links
  "Start checking twitter timeline for any url and merge if needed.
  If the given twitter list is empty then check twitter for our lists
  and create a new one."
  [tw-lists cfg] ; i hate this form/pattern need to clean that up
  (if (nil? tw-lists)
    (read-list-tweets (first (get-new-tw-lists cfg)))
    (read-list-tweets tw-lists)))

(defn read-cfg
  "We're given the tw list history dir (containing were we left off).
  1 file per twitter list we're tracking.
  Also age out entries that haven't been updated in configurable num of days."
  [cfg]
  (if (.exists (java.io.File. (:directory cfg)))
    (let [tw-lists (map #(read-string (slurp (.getAbsolutePath %)))
                        (.listFiles (java.io.File. (:directory cfg))))]
      (map (fn [tw-list]
             (assoc-in tw-list [:links]
                       (filter #(within? (interval 
                                           (-> (:days-to-expire cfg) days ago) 
                                           (now))
                                  (from-date (:last-activity %))) 
                               (:links tw-list))))
           tw-lists))
    (do (.mkdirs (java.io.File. (:directory cfg)))
        nil)))

; for cli args : https://github.com/clojure/tools.cli
(defn -main
  ([cfg] (update-tw-links (read-cfg cfg) cfg))
  ([] (-main {:directory "twlist" :days-to-expire 3 :tw-lists-to-track #{}})))

