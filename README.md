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


### Link summaries
The top urls are fetched and summarized using Luhn '58 algorithm, with a couple of minor changes. 

Luhn's algorithm makes a few assumptions in order to produce an automated summarization of a given text. 

The algorithm assumes that the topic of a text can be ascertained by using word frequencies (overly frequent words (like: the, and etc..) are eliminated by filtering them using "stop words" list of words, the words are also filtering using a "stemmer" which groups words based on similar prefix characters). Very rare words are also eliminated.

Sentences are then given a score and ranked based on how many significant words they contain.  

I've tried to make the implementation generic enough such that a user can specify an alternate algorithm for ranking sentences. The options are :luhn and a very basic algorithm that i also provided.

I've also provided an option for a user to choose how to eliminate non-significant words:

- freq: Frequency based. Using the mean and standard deviation calculate a threshold and filter words which are below that threshold. 
- pct: Simple percent based. For example, pick top 30% of words.
- top-n : Pick the top N words.

### Output format

Sumeet produces 2 sets of output:
 
- one file per twitter list, this file contains the tweets (the relevant parts). One thing to note is that i keep track of the tweet time and i age out tweets longer than 3 days (that option is configurable as well). The following is an example of the contents of the file:

      {:url "http://es.pn/19cch8b", :count 1, :rt-counts 38, :fav-counts 97, :urlers
      #{"Bill Simmons"}, :follow-count 2108550, :last-activity #inst "2013-06-16T22:13:36.483-00:00", :text #{"New BS Report: @ryenarussillo on Celts-Clips-Doc + NBA Draft. Lots of good nuggets/arguments in here. http://t.co/2C1MEdaB4o"}}

- one summary file containing the specified number of top tweets to summarize. The file is the same structure as for twitter list file, except that it adds another key :summary which has as it's value the summary of the url in the tweet.


By: twitter-handle
Neque porro quisquam est qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit...
link: http://www.example.com
Points: xxx
Brief Summary: the summary of text.

### Usage

FIXME

## License

Copyright © 2013 Bassem Youssef

Distributed under the Eclipse Public License, the same as Clojure.
