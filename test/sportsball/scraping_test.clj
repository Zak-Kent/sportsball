(ns sportsball.scraping-test
  (:require
   [clojure.test :refer :all]
   [sportsball.scrape :as sc]
   [sportsball.storage :as store]
   [sportsball.testutils :as tu]
   [metrics.counters :as mcount]
   [sportsball.slack :as slack]))

(deftest scrape-pulls-odds-infos
  (let [odds-infos (-> (slurp "dev-resources/live-game-scrape.html")
                       sc/sportsbookreview->odds-infos)]
    (is (= 15 (count odds-infos)))

    (testing "Check that all book odds have a value for home/away team odds"
      (is (true? (->> odds-infos
                      (map (fn [{books :books}]
                             (->> books
                                  vals
                                  (map vals)
                                  flatten)))
                      flatten
                      (not-any? nil?)))))))

(deftest scrape-sportsbookreview-stores-odds-info-bundles-in-db
  (tu/call-with-test-app-and-config
   (fn [app config]
     (let [odds-infos (-> (slurp "dev-resources/live-game-scrape.html")
                          sc/sportsbookreview->odds-infos)
           health-info (atom "")]
       (with-redefs [sc/html->data (fn [_ _] odds-infos)
                     slack/send-health-check
                     (fn [_ health-msg] (reset! health-info health-msg))]

         ;; trigger scrape, this will cause odds-infos above to be stored
         (sc/scrape-sportsbookreview config)

         (is (= [{:count 15}] (tu/all-odds config)))
         (is (= [{:count 15}] (tu/all-matchups config)))

         (testing "metrics update when scraping happens"
           (is (= 15 (mcount/value (-> config :metrics :odds-infos-found))))
           (is (= 200 (:status
                       (tu/mock-post app "/health-check" {:command "/health-check"}))))
           (is (= "Odds bundles found since last restart: 15"
                  @health-info))))))))
