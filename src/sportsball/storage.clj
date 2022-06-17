(ns sportsball.storage
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [java-time :as t]
            [clojure.string :as str]))

(def db {:dbtype "postgresql"
         :user "postgres"
         :port (-> (slurp "/Users/zakkent/Desktop/sportsball/box/port" )
                   read-string)
         :password (-> (slurp "/Users/zakkent/Desktop/sportsball/box/pgpass")
                       (str/split #":")
                       last
                       str/trim-newline)
         :dbname "sportsball"})

(def ^:dynamic *db* db)

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

(defn odds->matchup [odds]
  (let [ts (:timestamp odds)
        teams (->> (:teams odds)
                     vals
                     (str/join "-"))]
    ;; the rows returned by jdbc are namespaced maps
    #:matchup{:time ts :teams teams}))

(defn get-local-date [d]
  (t/format :iso-local-date
            (t/local-date-time d (t/zone-id))))

(defn check-matchup [matchup]
  "Takes an incoming matchup and returns a seq of any existing
   matchups for the same date already in the DB"
  (let [get-date (fn [m] (-> (:matchup/time m)
                         get-local-date))
        db-matchups (sql/query
                     *db*
                     ["select * from matchup where teams = ?"
                      (:matchup/teams matchup)])
        in-matchup (get-date matchup)]
    (filter (fn [m] (= (get-date m) in-matchup)) db-matchups)))

(defn store-matchup [odds-info]
  "Takes an incoming odds bundle decides whether or not to store
   a new matchup. Returns the matchup_id used as a FK in the odds
   table."
  (let [mup (odds->matchup odds-info)
        existing-mup (check-matchup mup)]
    (if (seq existing-mup)
      ;; TODO handle case if there are matching games on the same date
      ;; double headers, etc
      (:matchup/matchup_id (first existing-mup))
      (:matchup/matchup_id (sql/insert! *db* :matchup mup)))))

(defn store-odds [odds-info]
  (let [nil->0 #(if (nil? %) 0 %)
        get-score (fn [score]
                    (-> odds-info :game-score score nil->0))
        home-score (get-score :home-score)
        away-score (get-score :away-score)
        ts (:timestamp odds-info)
        match-id (store-matchup odds-info)
        lines (dissoc odds-info :teams :game-score :timestamp)
        odds-row {:home_score home-score
                  :away_score away-score
                  :time ts
                  :matchup_id match-id
                  :lines (with-meta lines {:pgtype "jsonb"})}]
    (sql/insert! *db* :odds odds-row)))
