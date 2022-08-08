(ns sportsball.storage
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [java-time :as t]
            [clojure.string :as str]
            ;; needed to load json protocol extensions for jdbc-next
            [sportsball.json :as _]
            [sportsball.slack :as slack]
            [sportsball.config :as config]
            [sportsball.sb-specs :as spec]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(def db (:db config/CONFIG))

(def ^:dynamic *db* db)

;; TODO: you might eventually want a way to gc old alerts
(def alert-registry (atom {}))

(def matchup-table-sql
  ["create table if not exists matchup (
      matchup_id SERIAL PRIMARY KEY,
      time TIMESTAMPTZ NOT NULL,
      teams VARCHAR NOT NULL)"])

(def odds-table-sql
  ["create table if not exists odds (
      time TIMESTAMPTZ NOT NULL,
      lines JSONB NOT NULL,
      home_score SMALLINT NOT NULL,
      away_score SMALLINT NOT NULL,
      matchup_id INTEGER NOT NULL,
      CONSTRAINT fk_matchup
        FOREIGN KEY (matchup_id)
          REFERENCES matchup(matchup_id))"])

(defn create-matchup-table []
  (jdbc/execute! *db* matchup-table-sql))

(defn create-odds-table []
  (jdbc/execute! *db* odds-table-sql))

(defn check-odds []
  (jdbc/execute! *db* [" SELECT * from odds; "]))

(defn game-info->matchup [game-info]
  (let [ts (:timestamp game-info)
        teams (->> (:teams game-info)
                     vals
                     (str/join "-"))]
    ;; the rows returned by jdbc are namespaced maps
    #:matchup{:time ts :teams teams}))

(defn get-local-date [d]
  (t/format :iso-local-date
            (t/local-date-time d (t/zone-id))))

(defn get-matchup-date [m]
  (-> (:matchup/time m) get-local-date))

(defn maybe-trigger [{:keys [home-threshold away-threshold]}
                     {:keys [home-odds away-odds]}
                     matchup]
  (let [send-alert (fn [odds threshold side]
                     (when threshold
                       (some->
                        (filter (fn [[book price]]
                                  (when (and price (> price threshold)) true))
                                odds)
                        (slack/send-threshold-alert matchup side))))]
    (send-alert home-odds home-threshold :home)
    (send-alert away-odds away-threshold :away)))

(defn odds-alert [odds]
  (let [matchup (-> odds
                    game-info->matchup
                    (update :matchup/time get-local-date))
        current-odds (-> odds :books)]
    (when-let [threshold (@alert-registry matchup)]
      (maybe-trigger threshold
                     (->> current-odds
                          (reduce (fn [acc [book {home :home-odds away :away-odds}]]
                                    (-> acc
                                        (assoc-in [:home-odds book] home)
                                        (assoc-in [:away-odds book] away)))
                                  {}))
                     matchup))))

(defn update-alerts [alert-req]
  (let [mu (-> alert-req
               game-info->matchup
               (update :matchup/time get-local-date))
        threshold (:thresholds alert-req)]
    (swap! alert-registry assoc mu threshold)))

(defn check-matchup [matchup]
  "Takes an incoming matchup and returns a seq of any existing
   matchups for the same date already in the DB"
  (let [db-matchups (sql/query
                     *db*
                     ["select * from matchup where teams = ?"
                      (:matchup/teams matchup)])
        in-matchup (get-matchup-date matchup)]
    (filter (fn [m] (= (get-matchup-date m) in-matchup)) db-matchups)))

(defn store-matchup [odds]
  "Takes an incoming odds bundle decides whether or not to store
   a new matchup. Returns the matchup_id used as a FK in the odds
   table."
  (let [mup (game-info->matchup odds)
        existing-mup (check-matchup mup)]
    (if (seq existing-mup)
      ;; TODO handle case if there are matching games on the same date
      ;; double headers, etc
      (:matchup/matchup_id (first existing-mup))
      (:matchup/matchup_id (sql/insert! *db* :matchup mup)))))

(defn store-odds [odds]
  (let [nil->0 #(if (nil? %) 0 %)
        get-score (fn [score]
                    (-> odds :game-score score nil->0))
        home-score (get-score :home-score)
        away-score (get-score :away-score)
        ts (:timestamp odds)
        match-id (store-matchup odds)
        lines (dissoc odds :teams :game-score :timestamp)
        odds-row {:home_score home-score
                  :away_score away-score
                  :time ts
                  :matchup_id match-id
                  :lines (with-meta lines {:pgtype "jsonb"})}]
    (odds-alert odds)
    (sql/insert! *db* :odds odds-row)))


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

(defn export-odds-csv []
  ;; TODO: add a way to specify a date range and dst file & path
  (let [db-odds (jdbc/execute! *db*
                               ["select odds.time, odds.lines, odds.home_score,
                                          odds.away_score, matchup.teams
                                 from odds
                                 join matchup on odds.matchup_id=matchup.matchup_id;"])
        header-row (-> db-odds first create-header-row)
        odds-rows (map odds-info->csv-row db-odds)]
    (with-open [writer (io/writer "sportsball-odds-data.csv")]
      (csv/write-csv writer (cons header-row odds-rows)))))
