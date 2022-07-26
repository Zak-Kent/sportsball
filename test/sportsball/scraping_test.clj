(ns sportsball.scraping-test
  (:require
   [clojure.test :refer :all]
   [sportsball.scrape :as sc]))

(deftest scrape-pulls-odds-infos
  (let [page-html (slurp "dev-resources/live-game-scrape.html")
        odds-infos (sc/sportsbookreview->odds-infos page-html)]

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
