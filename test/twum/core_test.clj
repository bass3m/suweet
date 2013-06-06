(ns twum.core-test
  (:use
   [twitter.oauth]
   [twitter.callbacks]
   [twitter.callbacks.handlers]
   [twitter.api.restful])
  (:import
   (twitter.callbacks.protocols SyncSingleCallback)) 
  (:use [clojure.test] 
        [twum.cfg :only (my-creds)] 
        [twum.core]))

(def cfg  {:directory "twtest" :days-to-expire 3 :tw-lists-to-track #{}}) 
(def my-tw-lists (get-new-tw-lists cfg))

(deftest test-tw-list-count
  (testing "Test getting twitter list count"
    (is (= 12 (count my-tw-lists)))))

; rewrite cfg and restrict it to only 1 twitter list
(def cfg (merge cfg {:tw-lists-to-track #{"dev"}}))

(deftest test-tw-list-dev
  (testing "Test getting twitter list and limiting to only dev list"
    (is (= 1 (count (get-new-tw-lists cfg ))))))

;; make sure we have a list called Dev with a slug of "dev"
(deftest test-list-dev-exists
  (testing "Test that the dev list exists in the returned twitter lists"
    (let [list-name (clojure.string/join [(:directory cfg) "/dev"])]
      (is (= (some #(= list-name (:list-name %)) (first  my-tw-lists)) 
             true)))))

; we have a non-zero since-id
(deftest test-validate-since-id-links
  (testing "Test that the since-id and links returned is greater than zero"
    (let [tweets (first (update-tw-links (read-cfg cfg) cfg))]
      (is (and (> (:since-id tweets) 0)
               (> (count (:links tweets)) 0))))))

