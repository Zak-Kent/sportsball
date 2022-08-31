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
            [sportsball.utils :as utils]
            [sportsball.csv :as sbcsv]))

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
  ([app endpoint body]
   (mock-post app endpoint body :json))
  ([app endpoint body typ]
   (let [body-fn (case typ
                   :json mock/json-body
                   :urlencoded mock/body)]
     (app (-> (mock/request :post endpoint)
              (body-fn body))))))

(deftest insert-new-matchup
  (tu/call-with-test-config
   (fn [config]
      (let [odds (gen-odds-info)]
        (store/store-matchup (:db config) odds)
        (is (= [{:count 1}] (tu/all-matchups config)))))))

(deftest same-day-same-game-storage
  (tu/call-with-test-config
   (fn [config]
      (let [odds (gen-odds-info)
            local-time (t/instant)
            local-time+1h (t/plus local-time (t/hours 1))
            store-match (fn [ts]
                          (store/store-odds
                           config
                           (assoc odds :timestamp (t/instant->sql-timestamp ts))))]
        ;; this test will fail if you happen to span midnight by an hour
        (store-match local-time)
        (store-match local-time+1h)
        (is (= [{:count 1}] (tu/all-matchups config)))
        (is (= [{:count 2}] (tu/all-odds config)))))))

(deftest same-matchup-diff-day-storage
  (tu/call-with-test-config
   (fn [config]
     (let [odds (gen-odds-info)
           local-time (t/instant)
           local-time+1d (t/plus local-time (t/days 1))
           store-match (fn [ts]
                         (store/store-odds
                          config
                          (assoc odds :timestamp (t/instant->sql-timestamp ts))))]
       (store-match local-time)
       (store-match local-time+1d)
       (is (= [{:count 2}] (tu/all-matchups config)))
       (is (= [{:count 2}] (tu/all-odds config)))))))

(deftest basic-storage-via-odds-endpoint
  (tu/call-with-test-app-and-config
   (fn [app config]
     (is (= 200 (:status (mock-post app "/odds" (gen-odds-info)))))
     (is (= [{:count 1}] (tu/all-matchups config)))
     (is (= [{:count 1}] (tu/all-odds config))))))

(deftest malformed-request-body-odds-endpoint
  (tu/call-with-test-app-and-config
   (fn [app _]
     (is (= 400
            (:status (mock-post app "/odds"
                                (-> (gen-odds-info)
                                    (update :timestamp (constantly nil))))))))))

(deftest malformed-request-body-alert-sub-endpoint
  (tu/call-with-test-app-and-config
   (fn [app _]
     (is (= 400 (:status (mock-post app "/alert-sub" {:not :correct})))))))

(deftest register-alert-via-alert-sub-endpoint
  (tu/call-with-test-app-and-config
   (fn [app config]
     (is (= 200 (:status (mock-post app "/alert-sub" (gen-alert-sub)))))
     (is (= 1 (count @(:alert-registry config)))))))

(deftest alert-triggered-when-odds-bundle-matches-element-in-registry
  (let [send-results (atom [])]
    (with-redefs [slack/send-threshold-alert
                  (fn [config book-prices _ _]
                    (reset! send-results book-prices))]
      (tu/call-with-test-app-and-config
       (fn [app _]
         (is (= 200
                (:status
                 (mock-post app "/alert-sub"
                            (-> (gen-odds-info)
                                (select-keys [:teams :timestamp])
                                (assoc :thresholds {:home-threshold 150}))))))
         (is (= 200 (:status (mock-post app "/odds" (gen-odds-info)))))
         ;; the intertops odds generated in the test data has the only home-odds better than 150
         (is (= [[:Caesars 357]] @send-results)))))))

(deftest register-alert-via-slack-form
  ;; can't send the slack alert reg ack msg in tests because response_url doesn't really exist
  (with-redefs [slack/post-msg (fn [_ _ _] :no-op)]
    (tu/call-with-test-app-and-config
     (fn [app config]
       (is (= 200 (:status
                   (mock-post app "/slack-alert-sub"
                              (slack-utils/mock-slack-alert-action-msg :register-button)
                              :urlencoded))))
       (is (= 1 (count @(:alert-registry config))))))))

(deftest slack-team-select-action-does-not-register-alert
  (tu/call-with-test-app-and-config
   (fn [app config]
     (is (= 200 (:status
                 (mock-post app "/slack-alert-sub"
                            (slack-utils/mock-slack-alert-action-msg :team-select)
                            :urlencoded))))
     (is (= 0 (count @(:alert-registry config)))))))

(deftest csv-export
  (tu/call-with-test-config
   (fn [config]
     (tu/with-temp-files
       (let [odds (gen-odds-info)
             local-time (t/instant)
             local-time+1d (t/plus local-time (t/days 1))
             store-match (fn [ts]
                           (store/store-odds
                            config
                            (assoc odds :timestamp (t/instant->sql-timestamp ts))))
             test-csv (io/file tu/temp-dir "test.csv")]
         (store-match local-time)
         (store-match local-time+1d)

         (sbcsv/export-odds-csv (:db config) test-csv)

         (let [[header & rows] (csv/read-csv (slurp test-csv))
               row-maps (utils/csv->row-maps header rows)]
           (is (= 2 (count row-maps)))
           (is (= "-357"
                  (:BetMGM-away (first row-maps))
                  (:BetMGM-away (second row-maps))))))))))

(deftest slack-csv-export-supports-date-ranges
  (let [orig-pull-data-for-csv-export sbcsv/pull-data-for-csv-export
        csv-results (atom [])
        csv-call-count (atom 0)
        csv-exports-complete (promise)]
    (with-redefs [slack/send-csv (fn [_ _] :no-op)
                  sbcsv/pull-data-for-csv-export
                  (fn [db date-range]
                    (let [results (orig-pull-data-for-csv-export db date-range)]
                      (swap! csv-call-count inc)
                      (swap! csv-results conj results)
                      (when (= 2 @csv-call-count)
                        (deliver csv-exports-complete :done))))]
      (tu/call-with-test-app-and-config
       (fn [app config]
         (let [odds (gen-odds-info)
               store-match (fn [ts]
                             (store/store-odds
                              config
                              (assoc odds :timestamp (t/instant->sql-timestamp ts))))
               test-inst (-> "2022-01-01"
                             t/local-date
                             (.atStartOfDay (t/zone-id "UTC"))
                             t/instant)
               test-inst+3d (t/plus test-inst (t/days 3))]

           (store-match test-inst)
           (store-match test-inst+3d)
           (is (= [{:count 2}] (tu/all-matchups config))))

         (testing "export command with no range sends all data"
           (is (= 200 (:status
                       (mock-post app "/export-csv" {:command "/export-csv"
                                                     :text ""})))))

         (testing "export command sends only data inside date range"
           (is (= 200 (:status
                       (mock-post app "/export-csv" {:command "/export-csv"
                                                     :text "2021-12-31 2022-01-02"})))))

         (testing "invalid date range format responds with 400"
           (is (= 400 (:status
                       (mock-post app "/export-csv" {:command "/export-csv"
                                                     :text "can't parse this as date range"})))))

         (testing "start time after end time responds with 400"
           (is (= 400 (:status
                       (mock-post app "/export-csv" {:command "/export-csv"
                                                     :text "2022-01-05 2022-01-03"})))))

         ;; block until both csv streamed requests above are finished
         (let [export-state (deref csv-exports-complete 5000 :stuck)]
           (when (= :stuck export-state)
             (throw (Exception. "csv export promise not delievered")))
           (is (= :done export-state)))

         (testing "streamed exports have the expected number of odds rows returned"
           (let [[all-odds odds-in-range] @csv-results]
             ;; both odds rows plus header row
             (is (= 3 (count all-odds)))

             ;; only one odds row plus header row
             (is (= 2 (count odds-in-range))))))))))
