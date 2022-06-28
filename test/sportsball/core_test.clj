(ns sportsball.core-test
  (:require [clojure.test :refer :all]
            [java-time :as t]
            [sportsball.testutils :as tu]
            [sportsball.json :as json]
            [next.jdbc.sql :as sql]
            [malli.generator :as mg]
            [sportsball.sb-specs :as sbspec]
            [sportsball.storage :as store]
            [ring.mock.request :as mock]))

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

(deftest same-day-same-game-storage
  (tu/with-test-db
    (let [odds (gen-odds-info)
          local-time (t/instant)
          local-time+1h (t/plus local-time (t/hours 1))
          store-match (fn [ts]
                        (store/store-odds
                         (assoc odds :timestamp (t/instant->sql-timestamp ts))))]
      ;; this test will fail if you happen to span midnight by an hour
      (store-match local-time)
      (store-match local-time+1h)
      (is (= [{:count 1}]
             (query-test-db "select count(*) from matchup")))
      (is (= [{:count 2}]
             (query-test-db "select count(*) from odds"))))))

(deftest same-matchup-diff-day-storage
  (tu/with-test-db
    (let [odds (gen-odds-info)
          local-time (t/instant)
          local-time+1d (t/plus local-time (t/days 1))
          store-match (fn [ts]
                        (store/store-odds
                         (assoc odds :timestamp (t/instant->sql-timestamp ts))))]
      (store-match local-time)
      (store-match local-time+1d)
      (is (= [{:count 2}]
             (query-test-db "select count(*) from matchup")))
      (is (= [{:count 2}]
             (query-test-db "select count(*) from odds"))))))

(deftest basic-storage-via-odds-endpoint
  (tu/with-http-app
    (is (= 200
           (:status (tu/*app* (-> (mock/request :post "/odds")
                                  (mock/json-body (gen-odds-info)))))))))

(deftest malformed-request-body-odds-endpoint
  (tu/with-http-app
    (is (= 400
           (:status
            (tu/*app* (-> (mock/request :post "/odds")
                          (mock/json-body
                           (-> (gen-odds-info)
                               (update :timestamp (constantly nil)))))))))))
