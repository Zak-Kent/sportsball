(ns sportsball.storage
  (:require [next.jdbc :as jdbc]
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

(def matchup-table-sql
  ["create table if not exists matchup (
      matchup_id INTEGER PRIMARY KEY,
      time TIMESTAMPTZ NOT NULL,
      matchup VARCHAR NOT NULL)"])

(def odds-table-sql
  ["create table if not exists odds (
      time TIMESTAMPTZ NOT NULL,
      lines JSONB NOT NULL,
      home_score SMALLINT NOT NULL,
      away_score SMALLINT NOT NULL,
      matchup_id INTEGER,
      CONSTRAINT fk_matchup
        FOREIGN KEY (matchup_id)
          REFERENCES matchup(matchup_id))"])

(defn create-matchup-table []
  (jdbc/execute! db matchup-table-sql))

(defn create-odds-table []
  (jdbc/execute! db odds-table-sql))

(defn check-odds []
  (jdbc/execute! db [" SELECT * from odds; "]))
