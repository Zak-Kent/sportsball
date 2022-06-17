(ns sportsball.core-test
  (:require [clojure.test :refer :all]
            [java-time :as t]
            [sportsball.testutils :as tu]
            [next.jdbc.sql :as sql]
            [malli.generator :as mg]
            [sportsball.sb-specs :as sbspec]
            [sportsball.storage :as store]))

(defn query-test-db [query]
  (sql/query store/*db* [query]))

(defn gen-odds-info
  ([] (gen-odds-info 42))
  ([seed]
   (-> (mg/generate sbspec/odds-info {:seed seed})
       (update :timestamp t/instant->sql-timestamp))))

(deftest insert-new-matchup
  (tu/with-test-db
    (let [odds (gen-odds-info)]
      (store/store-matchup odds)
      (is (= [{:count 1}] (query-test-db "select count(*) from matchup"))))))

(deftest matchup-on-same-day-no-additional-matchup-created
  (tu/with-test-db
    (let [odds (gen-odds-info)
          local-time (t/instant)
          local-time+1h (t/plus local-time (t/hours 1))
          store-match (fn [ts]
                        (store/store-matchup
                         (assoc odds :timestamp (t/instant->sql-timestamp ts))))]
      ;; this test will fail if you happen to span midnight by an hour
      (store-match local-time)
      (store-match local-time+1h)
      (is (= [{:count 1}]
             (query-test-db "select count(*) from matchup"))))))











(deftest matchup-on-diff-day-creates-new-mathcup
  (tu/with-test-db
    (let [odds (gen-odds-info)
          local-time (t/instant)
          local-time+1d (t/plus local-time (t/days 1))
          store-match (fn [ts]
                        (store/store-matchup
                         (assoc odds :timestamp (t/instant->sql-timestamp ts))))]
      (store-match local-time)
      (store-match local-time+1d)
      (is (= [{:count 2}]
             (query-test-db "select count(*) from matchup"))))))
