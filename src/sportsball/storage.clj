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

(def odds-table-sql ["create table if not exists odds (
                        time TIMESTAMPTZ NOT NULL,
                        sportsbook VARCHAR NOT NULL,
                        matchup VARCHAR NOT NULL,
                        home_line SMALLINT NOT NULL,
                        away_line SMALLINT NOT NULL,
                        home_score SMALLINT NOT NULL,
                        away_score SMALLINT NOT NULL)"])

(defn create-odds-table []
  (jdbc/execute! db odds-table-sql))

(defn check-odds []
  (jdbc/execute! db [" SELECT * from odds; "]))
