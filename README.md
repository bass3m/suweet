# suweet

A Clojure library designed to help trim and summarize my overflowing twitter feed.

The way i use twitter is probably different from everyone else, so this tool scratches a certain itch. No guarantee that it'll scratch yours as well.

The basic idea is to organize the people that i follow on twitter into lists based on certain interests, for example, cycling, pop culture, development etc..
Suweet then reads my twitter lists and attempts to sort and organize them based on  
their popularity by using retweet, favorited and followers counts. It then attempts to summarize the pages referred to by links embedded in the tweet. The number of tweets to summarize is configurable, right now it defaults to 10 tweets per twitter list.  
It tries to combine tweets which post duplicate urls (i hate when slate does this!).

The library is meant to be run periodically (every few hours) to collect and summarize tweets.
Right now i use a cron job for this task. I actually have 2 cron jobs: 1 to update tweets every few hours and another job to take the highest rank tweets and summarize the links (if the tweets contained links).

At the moment, i'm saving all data in files in a separate directory (that directory is configurable). However, in addition, I'm planning to have that information stored in a db.

The library utilizes the following libraries:

- [Apache Tika](http://tika.apache.org/): for retrieving contents from a URL.
- [clojure-opennlp](https://github.com/dakrone/clojure-opennlp): for tokenization and sentence detection.
- [Snowball Stemmer](http://snowball.tartarus.org/) : for English language word stemmer.
- [clojure-twitter](https://github.com/mattrepl/clojure-twitter) : for the twitter api.
- [clj-time](https://github.com/KirinDave/clj-time) : provides date/time utils for keeping track of and ageing old tweets.


### Link summaries
The top urls are fetched and summarized using Luhn '58 algorithm, with a couple of minor changes. 

Luhn's algorithm makes a few assumptions in order to produce an automated summarization of a given text. 

The algorithm assumes that the topic of a text can be ascertained by using word frequencies (overly frequent words (like: the, and etc..) are eliminated by filtering them using "stop words" list of words, the words are also filtering using a "stemmer" which groups words based on similar prefix characters). Very rare words are also eliminated.

Sentences are then given a score and ranked based on how many significant words they contain.  

I've tried to make the implementation generic enough such that a user can specify an alternate algorithm for ranking sentences. The options are :luhn and a very basic algorithm that i also provided.

I've also provided an option for a user to choose how to eliminate non-significant words:

- **freq**: Frequency based. Using the mean and standard deviation calculate a threshold and filter words which are below that threshold. 
- **pct**: Simple percent based. For example, pick top 30% of words.
- **top-n** : Pick the top N words.

### Output format

Sumeet produces 2 sets of output:
 
- one file per twitter list, this file contains the tweets (the relevant parts). One thing to note is that i keep track of the tweet time and i age out tweets longer than 3 days (that option is configurable as well). The following is an example of the contents of the file:

      {:url "http://es.pn/19cch8b", :count 1, :rt-counts 38, :fav-counts 97, :urlers
      #{"Bill Simmons"}, :follow-count 2108550, :last-activity #inst "2013-06-16T22:13:36.483-00:00", :text #{"New BS Report: @ryenarussillo on Celts-Clips-Doc + NBA Draft. Lots of good nuggets/arguments in here. http://t.co/2C1MEdaB4o"}}

- one summary file containing the specified number of top tweets to summarize. The file is the same structure as for twitter list file, except that it adds another key :summary which has as it's value the summary of the url in the tweet.

### Config file
An example of the config options available:

    {:tw-sort :default, :cfg-file "config.txt", :extension "-summ.txt", 
     :directory "twlist", :days-to-expire 3, :tw-lists-to-track #{}
     :tw-score :default, :top-tweets 7 
     :num-sentences 3 :algo {:type :luhn :params {:word-cluster-size 4}} 
     :score-algo {:type :freq :params {:freq-factor 0.5}} 
     :stop-words "models/english.txt"}

Following are the config options available:

-  : **cfg-file** - the path to the config file
-  : **directory**  - Directory where tweets from twitter list will be stored
-  : **days-to-expire** - number of days to keep tweets before ageing them out
-  : **tw-lists-to-track** - set containing which twitter list to track. If left empty then track all twitter lists.
-  : **top-tweets** - How many top ranked tweets per list to get.
-  : **extension** - File extension to use for storing tweet url text summaries.
-  : **tw-sort** - algorithm used to sort tweets by their score.
-  : **tw-score** - algorithm used to give a score to a tweet.
-  : **num-sentences** - maximum number of sentences to return for the summary
-  : **algo** - which algorithm to use.
   - : **type** - Current options are :luhn and :basic
   - : **params** - Parameters for Luhn's algorithm.
       - : **word-cluster-size** - the cluster size, values of 4 or 5 are suggested by Luhn's paper.
- : **score-algo** - Which scoring algorithm to use
    - : **type** : Types available are :freq, :pct and :top-n
    - : **params** : Parameters for scoring algorithm.
        - : **freq-factor** - for freq scoring, factor to use set word score threshold.
        
          `score-threshold = mean + (freq-factor * standand-deviation)`
- : **stop-words** - path to the stop words file.



### Usage

The included config file has a default configuration that should be reasonable for most uses. However, feel free to edit the file to your satisfaction.

We can run the application using leiningen, as follows:

`lein run -- --cfg-file "config.txt" --directory "twlist" etc..`

If you decide to provide options on the command line, then the only **mandatory** option is to specify the location of the config file.
Please note that you can override the defaults in the config file using the command line options (these new config options will be then merged and saved to the config file).
You can additionally specify any of the config options stated above when running the application Just prefix that option with "--" the option name and the new value.  
Exceptions are "*algo*" and "*score-algo*", you have to manually edit the config file to change those options. 

From the repl :

Getting highest scoring tweets from twitter list name "**sports**":

    (top-tweets-from {:cfg-file "config.txt"} "sports")
    => By: Michael Cox
    RT @TahitiFootball: GOAALLLLLLLLLLLLLLLLLLLLLLLLLL!!!!!!!!!!!!!!!!!!!! TAHITI!!!!!!! WE ARE THE CHAMPIONS!!!! #NeverGiveUp Tehau HERO!!!!!!
    ReTweeted by: 8623 Favorited by: 0 Followers: 94060
    
    By: ESPN Stats & Info
    LeBron James 31.5 PPG in 11 games when facing elimination. @ESPNStatsInfo that's the highest PPG in such games in NBA history (min. 5 games)
    ReTweeted by: 291 Favorited by: 56 Followers: 552678
    
    By: Grantland
    'Yeezus' is the hedonistic unraveling of a man on the precipice fatherhood, by @Steven_Hyden http://t.co/0KxTlu2R8T
    Link: http://es.pn/12ESIVV
    ReTweeted by: 27 Favorited by: 55 Followers: 235467
    
    etc..
    
Getting text summary from my twitter list name "**dev**":

    (summarize-from {:cfg-file "config.txt"} "dev")
    old is new again: Stealing from the arcade\n\nBack in 1985, games like Super Mario Bros. popularized the effect of horizontal parallax scrolling – a technique wherein the background moves at a slower speed relative to the foreground, giving the impression etc…
    
    

#### Note on twitter credentials
In order to be able to use the twitter api you need to supply the application with your twitter oauth credentials as follows:

- Go to the Twitter developer page and create a new [application](https://dev.twitter.com/apps/new) and obtain your **Consumer key**, **Consumer secret**, **Access token** and **Access token secret**.
- Edit *cfg.clj* and use those values as strings in the *my-creds* def.

## License

Copyright © 2013 Bassem Youssef

Distributed under the Eclipse Public License, the same as Clojure.
