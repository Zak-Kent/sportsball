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

(defn all-matchups []
  (query-test-db "select count(*) from matchup"))

(defn all-odds []
  (query-test-db "select count(*) from odds"))

(defn gen-odds-info
  ([] (gen-odds-info 42))
  ([seed]
   (-> (mg/generate sbspec/odds-info {:seed seed})
       (update :timestamp t/instant->sql-timestamp))))

(defn gen-alert-sub
  ([] (gen-alert-sub 5))
  ([seed]
   (-> (mg/generate sbspec/alert-sub {:seed seed})
       (update :timestamp t/instant->sql-timestamp))))

(defn mock-post [endpoint body]
  (tu/*app* (-> (mock/request :post endpoint)
                (mock/json-body body))))

(deftest insert-new-matchup
  (tu/with-test-db
    (let [odds (gen-odds-info)]
      (store/store-matchup odds)
      (is (= [{:count 1}] (all-matchups))))))

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
      (is (= [{:count 1}] (all-matchups)))
      (is (= [{:count 2}] (all-odds))))))

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
      (is (= [{:count 2}] (all-matchups)))
      (is (= [{:count 2}] (all-odds))))))

(deftest basic-storage-via-odds-endpoint
  (tu/with-http-app
    (is (= 200 (:status (mock-post "/odds" (gen-odds-info)))))
    (is (= [{:count 1}] (all-matchups)))
    (is (= [{:count 1}] (all-odds)))))

(deftest malformed-request-body-odds-endpoint
  (tu/with-http-app
    (is (= 400
           (:status (mock-post "/odds"
                     (-> (gen-odds-info)
                         (update :timestamp (constantly nil)))))))))

(deftest malformed-request-body-alert-sub-endpoint
  (tu/with-http-app
    (is (= 400 (:status (mock-post "/alert-sub" {:not :correct}))))))

(deftest register-alert-via-alert-sub-endpoint
  (tu/with-http-app
    (is (= 200 (:status (mock-post "/alert-sub" (gen-alert-sub)))))
    (is (= 1 (count @store/alert-registry)))))

(deftest alert-triggered-when-odds-bundle-matches-element-in-registry
  (let [call-count (atom 0)]
    (with-redefs [store/trigger-alert (fn [_] (swap! call-count inc))]
      (tu/with-http-app
        (is (= 200
               (:status (mock-post "/alert-sub"
                                   (-> (gen-odds-info)
                                       (select-keys [:teams :timestamp])
                                       (assoc :thresholds {:home-threshold -150}))))))
        (is (= 200 (:status (mock-post "/odds" (gen-odds-info)))))
        (is (= 1 @call-count))))))
