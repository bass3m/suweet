# Introduction to twum

TODO: write [great documentation](http://jacobian.org/writing/great-documentation/what-to-write/)

Load config from file (files in json format)
also incorporate fav count and retweet count in the sorting 
should save last tweet id so we can start from it

query twitter api every 10 minutes or so and grab tweets from some last id
merge urls with our list. Keep a time stamp of when url was added
and expire the url after 15 days or so.
store in file in json format for flexibility.
summarize links (that would be great).
Step 0:
  State: you save state in files. 
  1 file for each list contains: 
    (what to do about list names with slashes)? could use the list id here
    twitter auto converts the slashes to dashes: e.g. "egypt-middleeast" using the slug of the list
    list id etc.. 
    last tweet id
    top url table:
      url info to save: url, tweet text, how many time fav'ed and RTed
      time stamp to expire url that haven't been updated to 3 days or so. 
Step 1:
  Get my lists
  for all my lists get tweets 
  parse for the urls (and the tweet text etc..), it would be nice
    to keep that info as well.
  add to url table and increment count if needed

[{:list-name "Dev" :slug "othername" :list-id "xxxx" :since-id "abcd"
  :links [{:count 17 :urlers ["person1" "person2" etc...]
           :expanded_url "hhs.com" :rt_count 8 :fav_count 3
           :text "the tweet text"}, {} etc...]},
 {:list-name "PopCulture" ....
  :links [{link1} ,{}]}]
           
(pprint (filter #(= (:slug %) "dev") (:body (twitter.api.restful/lists-list :oauth-creds my-creds))))

usage:
twum --list dev , prog --d directory/path
for now just leave things as default
