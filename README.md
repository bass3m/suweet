# twum

A Clojure library designed to help summarize my overflowing twitter feed.
The way i use twitter is probably different from everyone else, so this tool
scratches my itch. No guarantee that it'll scratch your's as well.
The idea is to organize the people that i follow on twitter into lists based on
certain interests. i.e. cycling, friends, development etc..
This library reads my twitter lists and attempts to sort and organize them based on  
their popularity by using retweet counts and favorited counts. It tries to combine
tweets which post duplicate urls (i hate when slate does this !).
The app is meant to be run periodically (every few hours) to collect tweets.
I'm planning to have it update it's results every day and to have that data
accessible via email or db or json (for website).

## Link summaries
The top urls can then be fetched and summarized using Luhn '58 algorithm,
with a couple of changes.

## Report format

By: twitter-handle
Neque porro quisquam est qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit...
link: http://www.example.com
Points: xxx
Brief Summary: the summary of text.

## Usage

FIXME

## License

Copyright © 2013 Bassem Youssef

Distributed under the Eclipse Public License, the same as Clojure.
