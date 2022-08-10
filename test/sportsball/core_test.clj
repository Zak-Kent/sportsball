(ns sportsball.core-test
  (:require [clojure.test :refer :all]
            [java-time :as t]
            [sportsball.testutils :as tu]
            ;; needed to load json protocol extensions for jdbc-next
            [sportsball.json :as _]
            [malli.generator :as mg]
            [sportsball.sb-specs :as sbspec]
            [sportsball.storage :as store]
            [sportsball.slack-testutils :as slack-utils]
            [ring.mock.request :as mock]
            [sportsball.slack :as slack]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [sportsball.utils :as utils]))

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

(defn mock-post
  ([endpoint body]
   (mock-post endpoint body :json))
  ([endpoint body typ]
   (let [body-fn (case typ
                   :json mock/json-body
                   :urlencoded mock/body)]
     (tu/*app* (-> (mock/request :post endpoint)
                   (body-fn body))))))

(deftest insert-new-matchup
  (tu/with-test-db
    (let [odds (gen-odds-info)]
      (store/store-matchup odds)
      (is (= [{:count 1}] (tu/all-matchups))))))

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
      (is (= [{:count 1}] (tu/all-matchups)))
      (is (= [{:count 2}] (tu/all-odds))))))

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
      (is (= [{:count 2}] (tu/all-matchups)))
      (is (= [{:count 2}] (tu/all-odds))))))

(deftest basic-storage-via-odds-endpoint
  (tu/with-http-app
    (is (= 200 (:status (mock-post "/odds" (gen-odds-info)))))
    (is (= [{:count 1}] (tu/all-matchups)))
    (is (= [{:count 1}] (tu/all-odds)))))

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
  (let [send-results (atom [])]
    (with-redefs [slack/send-threshold-alert
                  (fn [book-prices _ _] (reset! send-results book-prices))]
      (tu/with-http-app
        (is (= 200
               (:status
                (mock-post "/alert-sub"
                           (-> (gen-odds-info)
                               (select-keys [:teams :timestamp])
                               (assoc :thresholds {:home-threshold 150}))))))
        (is (= 200 (:status (mock-post "/odds" (gen-odds-info)))))
        ;; the intertops odds generated in the test data has the only home-odds better than 150
        (is (= [[:intertops 357]] @send-results))))))

(deftest register-alert-via-slack-form
  ;; can't send the slack alert reg ack msg in tests because response_url doesn't really exist
  (with-redefs [slack/post-msg (fn [_ _] :no-op)]
    (tu/with-http-app
      (is (= 200 (:status
                  (mock-post "/slack-alert-sub"
                             (slack-utils/mock-slack-alert-action-msg :register-button)
                             :urlencoded))))
      (is (= 1 (count @store/alert-registry))))))

(deftest slack-team-select-action-does-not-register-alert
  (tu/with-http-app
    (is (= 200 (:status
                (mock-post "/slack-alert-sub"
                           (slack-utils/mock-slack-alert-action-msg :team-select)
                           :urlencoded))))
    (is (= 0 (count @store/alert-registry)))))

(deftest csv-export
  (tu/with-test-db
    (tu/with-temp-files
      (let [odds (gen-odds-info)
            local-time (t/instant)
            local-time+1d (t/plus local-time (t/days 1))
            store-match (fn [ts]
                          (store/store-odds
                           (assoc odds :timestamp (t/instant->sql-timestamp ts))))
            test-csv (io/file tu/temp-dir "test.csv")]
        (store-match local-time)
        (store-match local-time+1d)

        (store/export-odds-csv test-csv)

        (let [[header & rows] (csv/read-csv (slurp test-csv))
              row-maps (utils/csv->row-maps header rows)]
          (is (= 2 (count row-maps)))
          (is (= "200"
                 (:BetMGM-away (first row-maps))
                 (:BetMGM-away (second row-maps)))))))))
