(ns sportsball.core-test
  (:require [clojure.test :refer :all]
            [java-time :as t]
            [sportsball.testutils :as tu]
            [next.jdbc.sql :as sql]
            [malli.generator :as mg]
            [sportsball.sb-specs :as sbspec]
            [sportsball.storage :as store]))

(defn test-odds-insert []
  (sql/insert! store/*db* :odds (tu/gen-fake-odds-info)))

(defn query-test-db [query]
  (sql/query store/*db* [query]))

(defn gen-odds-info
  ([] (gen-odds-info 42))
  ([seed]
   (-> (mg/generate sbspec/odds-info {:seed seed})
       (update :timestamp t/instant->sql-timestamp))))

(deftest insert-fake-row
  (tu/with-test-db
    (test-odds-insert)
    (is (= [{:count 1}] (query-test-db "select count(*) from odds")))))

(deftest insert-new-matchup
  (tu/with-test-db
    (let [odds (gen-odds-info)]
      (store/store-matchup odds)
      (is (= [{:count 1}] (query-test-db "select count(*) from matchup"))))))
