(ns sportsball.scraping-test
  (:require
   [clojure.test :refer :all]
   [sportsball.scrape :as sc]
   [sportsball.storage :as store]
   [sportsball.testutils :as tu]
   [metrics.counters :as mcount]))

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
  (tu/call-with-test-config
   (fn [config]
     (let [odds-infos (-> (slurp "dev-resources/live-game-scrape.html")
                          sc/sportsbookreview->odds-infos)]
       (with-redefs [sc/html->data (fn [_ _] odds-infos)]

         ;; trigger scrape, this will cause odds-infos above to be stored
         (sc/scrape-sportsbookreview config)

         (is (= [{:count 15}] (tu/all-odds config)))
         (is (= [{:count 15}] (tu/all-matchups config)))

         (is (= 15 (mcount/value (-> config :metrics :odds-infos-found)))))))))
