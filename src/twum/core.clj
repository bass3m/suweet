(ns twum.core
  (:require
    [clojure.java.io :as io :only [as-file]]
    [twitter.api.restful]
    [clj-time.core :as clj-time]
    [clj-time.coerce :as coerce]
    [twum.score :as score :only [top-tw-list]]
    [twum.links :as link]
    [twum.summarize :as s :only [summarize]]
    [twum.cfg :as cfg :only [my-creds]]))

(defn new-tw-list []
  {:list-name ""
   :list-id ""
   :since-id 0
   ; links will contain a multitude of links
   :links []})

(defn get-twitter-lists
  "Query twitter for our lists. Returns vector of maps."
  []
  (:body  (twitter.api.restful/lists-list :oauth-creds cfg/my-creds)))

(defn get-expanded-urls-from-tw [tweets]
  (map (comp :expanded_url first :urls :entities) tweets))

(defn get-tweet-ids [tweets]
  (map :id tweets))

(defn get-latest-tweet-id [tw-list tweets]
  (update-in tw-list [:since-id] #(apply max (flatten [% (map :id tweets)]))))

(defn process-tweet
  "Process an individual tweet. We only care about urls(contained in
  entities),text, fav count and rt count."
  ; if it's a new link then create a new link
  ; otherwise update the existing entry: by increasing count and adding
  ; another urlers to the set, also text and update the last activity
  [{:keys [links] :as tw-list}
   {:keys [text favorite_count retweet_count entities user]}]
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
                 :count 1 :rt-counts retweet_count
                 :fav-counts favorite_count
                 :urlers (hash-set (:name user))
                 :follow-count (:followers_count user)
                 :last-activity (java.util.Date.)
                 :text (hash-set text)})))

(defn process-list-tweets
  "Call twitter api for a list and process the tweets"
  [tw-list twitter-params]
  (let [tweets (:body (twitter.api.restful/lists-statuses
                        :oauth-creds cfg/my-creds
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
    (assoc (new-tw-list)
            :list-name (str (:directory cfg) "/" slug)
            :list-id id)))

(defn update-tw-links
  "Start checking twitter timeline for any url and merge if needed.
  If the given twitter list is empty then check twitter for our lists
  and create a new one."
  [tw-lists cfg] ; i hate this form/pattern need to clean that up
  (if (nil? tw-lists)
    (read-list-tweets (get-new-tw-lists cfg))
    (read-list-tweets tw-lists)))

; age out old entries
(defn age-old-tweets
  "Age out tweets that are older than a configured number of days"
  [tw-list days-to-keep]
  (assoc-in tw-list [:links]
            (vec (filter #(clj-time/within? (clj-time/interval
                                     (-> days-to-keep clj-time/days clj-time/ago)
                                     (clj-time/now))
                            (coerce/from-date (:last-activity %)))
                         (:links tw-list)))))

(defn merge-cfg
  "Read our config file and merge it with the config supplied
  from the user. User configs overwrite config file settings."
  [new-cfg]
  (let [cfg-file (:cfg-file new-cfg)]
    (if (empty? cfg-file)
      new-cfg
      (if (.exists (io/as-file cfg-file))
        (let [existing-cfg (read-string (slurp cfg-file))]
          ;; now we merge the exiting config with what the user specified
          (reduce (fn [acc [k v]]
                     (if (not (contains? acc k))
                       (merge acc (hash-map k v))
                       acc)) new-cfg existing-cfg))
        new-cfg))))

(defn write-cfg
  "Write our last config to a file. Overwrite existing file if exists."
  [cfg]
  (do
    (when (not (empty? (:cfg-file cfg)))
      (spit (:cfg-file cfg) cfg))
    cfg))

(defn read-tw-lists
  "Slurp our lists."
  [cfg]
  (map #(read-string (slurp (.getAbsolutePath %)))
       (.listFiles (io/as-file (:directory cfg)))))

(defn top-tweets
  "Process all of our lists for the top tweets in each list.
  We're given the location of the config file"
  ([] (top-tweets {:cfg-file "config.txt" :directory "twlist"
                   :top-tweets 10 :tw-sort :default :tw-score :default}))
  ([cfg] (map (partial score/top-tw-list cfg) (-> cfg
                                                  merge-cfg
                                                  read-tw-lists))))

(defn save-top-tw-list
  "Save a tw list's top tweets"
  [cfg tw-list]
  (do (spit (str (:list-name tw-list) (:extension cfg)) (pr-str tw-list))
      tw-list))

; want to make them composable/partial
(defn save-top-tweets
  "Save top tweets to be read at a later time"
  ([tw-lists] (save-top-tweets {:cfg-file "config.txt" :directory "twlist"
                                :top-tweets 10 :tw-sort :default
                                :tw-score :default :extension "-summ.txt"}
                               tw-lists))
  ([cfg tw-lists] (map (partial save-top-tw-list cfg) tw-lists)))

(defn summarize-link
  "Summarize an individual link, if no link is given then ignore"
  [cfg tweet]
  (if-let [url (:url tweet)]
    (assoc tweet :summary (map :sentence (-> url
                                             link/parse-url
                                             link/clean-html
                                             s/summarize)))
    tweet))

(defn summarize-tw-list
  "Given the top tweets, go through any links and summarize the text"
  [cfg tw-list]
  (map (partial summarize-link cfg) tw-list))

;; since we got rid of the formating (we can do at the final stage
;; before saving), then we should have all the info that we need.
(defn summarize-top-tweets
  [cfg]
  (map (comp (partial save-top-tweets cfg)
             (partial summarize-tw-list cfg)
             (partial score/top-tw-list cfg)) (-> cfg
                                                  merge-cfg
                                                  read-tw-lists)))

(defn read-tw-list-by-name
  "Read the given twlist"
  [cfg twlist-name]
  (let [twlist-filename (str (:directory cfg) "/" twlist-name)]
    (if (.exists (io/as-file twlist-filename))
      (read-string (slurp twlist-filename)))))

(defn read-old-tweets
  "We're given the tw list history dir (containing were we left off).
  1 file per twitter list we're tracking.
  Also age out entries that haven't been updated in configurable num of days.
  This function creates the twlist directory if it doesn't exists already."
  [cfg]
  (if (.exists (io/as-file (:directory cfg)))
    (let [tw-lists (read-tw-lists cfg)]
      (map #(age-old-tweets % (:days-to-expire cfg)) tw-lists))
    (do (.mkdirs (io/as-file (:directory cfg)))
        nil)))

; for cli args : https://github.com/clojure/tools.cli
(defn -main
  ([cfg] (-> cfg
             merge-cfg
             write-cfg
             read-old-tweets
             (update-tw-links cfg)))
  ([] (-main {:directory "twlist"
              :days-to-expire 3
              :tw-lists-to-track #{}
              :cfg-file "config.txt"})))

