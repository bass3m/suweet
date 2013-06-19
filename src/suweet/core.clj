(ns twum.core
  (:require
    [clojure.java.io :as io :only [as-file]]
    [clojure.tools.cli :as cli :only [cli]]
    [twitter.api.restful]
    [clj-time.core :as clj-time]
    [clj-time.coerce :as coerce]
    [twum.score :as score :only [top-tw-list format-top-tweet]]
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
  (:body (twitter.api.restful/lists-list :oauth-creds cfg/my-creds)))

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

(defn print-tw-list-update-summary
  [tw-lists]
  (do
    (println "Updates to" (count tw-lists) "lists")
    (println (map (comp
                    (fn [[list-name num-links]]
                      (format (str "List: " list-name " - Num of tweets " num-links "\n")))
                    #((juxt :list-name (comp count :links)) %)) tw-lists))))

(defn read-list-tweets
  "Query twitter for tweets for each of our lists"
  [tw-lists]
  (let [tw-lists (map get-twitter-list-tweets tw-lists)]
    (print-tw-list-update-summary tw-lists)))

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
  [tw-lists cfg]
  (read-list-tweets (get-new-tw-lists cfg)))

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
    (when (not-empty (:cfg-file cfg))
      (spit (:cfg-file cfg) (with-out-str (clojure.pprint/pprint cfg))))
      cfg))

(defn read-tw-list
  "Read a saved tw list from file"
  [file-path]
  (let [file-contents (slurp (.getAbsolutePath file-path))]
    (when (re-find #"\{\:list\-name" file-contents)
      (read-string file-contents))))

(defn read-tw-lists
  "Slurp our lists."
  [cfg]
  (for [file (.listFiles (io/as-file (:directory cfg)))
        :let [file-contents (read-tw-list file)] :when file-contents]
    file-contents))

(defn top-tweets
  "Process all of our lists for the top tweets in each list.
  We're given the location of the config file"
  ([] (top-tweets {:cfg-file "config.txt" :directory "twlist"
                   :top-tweets 10 :tw-sort :default :tw-score :default}))
  ([cfg] (let [cfg (merge-cfg cfg)]
           (map (partial score/top-tw-list cfg) (-> cfg
                                                    write-cfg
                                                    read-tw-lists)))))

(defn save-top-tw-list
  "Save a tw list's top tweets"
  [cfg tw-list]
  (spit (str (:list-name tw-list) (:extension cfg)) (pr-str (:links tw-list))))

(defn summarize-link
  "Summarize an individual link, if no link is given then ignore"
  [cfg tweet]
  (if-let [url (:url tweet)]
    (assoc tweet :summary (vec (map :sentence (-> url
                                                  link/parse-url
                                                  link/clean-html
                                                  s/summarize))))
    tweet))

(defn summarize-tw-list
  "Given the top tweets, go through any links and summarize the text"
  [cfg tw-list]
  (assoc-in tw-list [:links] (vec (map (partial summarize-link cfg) (:links tw-list)))))

(defn summarize-top-tweets
  [cfg]
  (let [cfg (merge-cfg cfg)]
    (map (comp (partial save-top-tw-list cfg)
               (partial summarize-tw-list cfg)
               (partial score/top-tw-list cfg)) (-> cfg
                                                    write-cfg
                                                    read-tw-lists))))

(defn read-tw-list-by-name
  "Read the given twlist"
  [cfg twlist-name]
  (let [twlist-filename (str (:directory (merge-cfg cfg)) "/" twlist-name)]
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

(defn get-from
  "Get from given twlist name"
  [twlist-name f cfg]
  (f (read-tw-list-by-name cfg twlist-name)))

(defn top-tweets-from
  "Return the top tweets from given twitter list in a user viewable format"
  [cfg twlist-name]
  (get-from twlist-name score/format-top-tweets cfg))

(defn summarize-from
  "Return summaries of text from a given twitter list. We're given the
  twiiter list name, we need to append the summary extension from the config
  in order to read the summary file"
  [cfg twlist-name]
  (let [cfg (merge-cfg cfg)]
    (get-from (str twlist-name (:extension cfg)) s/format-summary cfg)))

(defn -main [& args]
  (let [[opts _ banner] 
        (cli/cli args
                 ["--cfg-file" "Path to config file location"
                  :default "config.txt"]
                 ["--directory" "Directory where twitter lists reside"
                  :default "twlist"]
                 ["--days-to-expire"
                  "Number of days to keep tweets before they age-out"
                  :default 3 :parse-fn #(Integer. %)]
                 ["--extension"
                  "File extension for storing high score tweets' url text summaries"
                  :default "-summ.txt"]
                 ["--top-tweets" "How many top ranked tweets per list to get"
                  :default 10 :parse-fn #(Integer. %)]
                 ["--num-sentences"
                  "Maximum number of sentences to return for the summary"
                  :default 3 :parse-fn #(Integer. %)]
                 ["--stop-words" "Path to Stop Words file"
                  :default "models/english.txt"]
                 ["--tw-lists-to-track"
                  "Space-separated string of twitter lists names. All if not specified"
                  :default #{} :parse-fn #(into #{} (clojure.string/split % #" "))]
                 ["--help" "Show help" :default false :flag true])
        {:keys [help]} opts]
    (when help
      (println "Suweet" banner)
      (System/exit 0))
    (-> opts
        merge-cfg
        write-cfg
        read-old-tweets
        (update-tw-links opts))))