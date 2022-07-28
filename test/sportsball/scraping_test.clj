(ns sportsball.scraping-test
  (:require
   [clojure.test :refer :all]
   [sportsball.scrape :as sc]
   [sportsball.storage :as store]
   [sportsball.testutils :as tu]))

(deftest scrape-pulls-odds-infos
  (let [odds-infos (-> (slurp "dev-resources/live-game-scrape.html")
                       sc/sportsbookreview->odds-infos)]

    ;; 21 games are present in page but only 12 have valid odds-info
    ;; information the rest are filtered out during scraping
    (is (= 12 (count odds-infos)))

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
  (tu/with-test-db
    (let [odds-infos (-> (slurp "dev-resources/live-game-scrape.html")
                         sc/sportsbookreview->odds-infos)]
      (with-redefs [sc/html->data (fn [_ _] odds-infos)]

        ;; trigger scrape, this will cause odds-infos above to be stored
        (sc/scrape-sportsbookreview)

        (is (= [{:count 12}] (tu/all-odds)))
        (is (= [{:count 12}] (tu/all-matchups)))))))
