(ns sportsball.csv
  (:require
   [clojure.data.csv :as csv]
   [clojure.java.io :as io]
   [next.jdbc :as jdbc]
   [sportsball.storage :as store]
   [sportsball.sb-specs :as spec]
   [sportsball.config :as config]
   [sportsball.slack :as slack]
   [clojure.string :as str])
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
  ([db]
   (pull-data-for-csv-export db nil))
  ([db {:keys [start end] :as date-range}]
   (let [base-query "select odds.time, odds.lines, odds.home_score,
                            odds.away_score, matchup.teams
                      from odds
                      join matchup on odds.matchup_id=matchup.matchup_id"
         ranged-query (if (seq date-range)
                        (-> (str/join
                             " "
                             [base-query "where odds.time >= ?::timestamp and odds.time < ?::timestamp"])
                            list
                            (concat [start end]))
                        [base-query])
         db-odds (jdbc/execute! db ranged-query)
         header-row (-> db-odds first create-header-row)
         odds-rows (map odds-info->csv-row db-odds)]
     (cons header-row odds-rows))))

(defn export-odds-csv [db dst]
  (with-open [writer (io/writer dst)]
    (csv/write-csv writer (pull-data-for-csv-export db))))

(defn create-csv-stream [db date-range]
  (let [in-stream (new PipedInputStream)
        out-stream (PipedOutputStream. in-stream)]
    (.start (Thread.
             #(with-open [writer (io/writer out-stream)]
                (csv/write-csv writer (pull-data-for-csv-export db date-range)))))
    in-stream))

(defn send-slack-csv [{:keys [db slack-conn-info]} date-range]
  (slack/send-csv slack-conn-info (create-csv-stream db date-range)))
