(ns suweet.cfg
  (:require [twitter.oauth]))

(def access-token-key "YOUR-ACCESS-TOKEN-KEY")
(def access-token-secret "YOUR-ACCESS-TOKEN-SECRET")
(def app-consumer-key "YOUR-APP-CONSUMER-KEY")
(def app-consumer-secret "YOUR-APP-CONSUMER-SECRET")

(def my-creds (twitter.oauth/make-oauth-creds app-consumer-key
                                              app-consumer-secret
                                              access-token-key
                                              access-token-secret))

