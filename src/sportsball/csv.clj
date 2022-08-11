(ns sportsball.csv
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [next.jdbc :as jdbc]
   [sportsball.storage :as store]
   [sportsball.sb-specs :as spec]
   [sportsball.config :as config])
  (:import [java.io PipedInputStream PipedOutputStream]))

;; CSV export
(defn create-header-row [odds-info]
  (let [game-info-headers ["time" "teams" "away-score" "home-score"]
        csv-book-odds (fn [b]
                        (let [bn (name b)]
                          [(str bn "-away") (str bn "-home")]))]
    (->> spec/book-keys
         (map csv-book-odds)
         (apply concat)
         (concat game-info-headers))))

(defn get-game-info [{:keys [odds/time odds/away_score
                             odds/home_score matchup/teams]}]
  [(str time) teams away_score home_score])

(defn lookup-odds [lines]
  (let [extract-odds-vals
        (fn [bk] (let [[away home] (-> bk lines vals)]
                   [away home]))]
    (->> spec/book-keys
         (map extract-odds-vals)
         (apply concat))))

(defn odds-info->csv-row [odds-info]
  (let [game-info (get-game-info odds-info)
        book-odds (-> odds-info :odds/lines :books)]
    (concat game-info (lookup-odds book-odds))))

(defn pull-data-for-csv-export
  "pull all the data from the odds table"
  []
  ;; TODO: add a way to specify a date range
  ;; TODO: may need to adjust to streaming results when size grows
  (let [db-odds (jdbc/execute! store/*db*
                               ["select odds.time, odds.lines, odds.home_score,
                                          odds.away_score, matchup.teams
                                 from odds
                                 join matchup on odds.matchup_id=matchup.matchup_id;"])
        header-row (-> db-odds first create-header-row)
        odds-rows (map odds-info->csv-row db-odds)]
    (cons header-row odds-rows)))

(defn export-odds-csv [dst]
  (with-open [writer (io/writer dst)]
    (csv/write-csv writer (pull-data-for-csv-export))))
