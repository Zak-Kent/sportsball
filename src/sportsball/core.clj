(ns sportsball.core
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [java-time :as t])
  (:import java.util.Date)
  (:gen-class))

(def db {:dbtype "postgresql"
         :user "zakkent"
         :port (-> (slurp "/Users/zakkent/Desktop/sportsball/box/port" )
                   read-string)
         :password (-> (slurp "/Users/zakkent/Desktop/sportsball/box/pgpass")
                       (str/split #":")
                       last
                       str/trim-newline)
         :dbname "sportsball"})

(defn create-odds-table []
  (jdbc/execute! db ["create table if not exists odds (
                        time TIMESTAMPTZ NOT NULL,
                        sportsbook VARCHAR NOT NULL,
                        matchup VARCHAR NOT NULL,
                        home_line SMALLINT NOT NULL,
                        away_line SMALLINT NOT NULL,
                        home_score SMALLINT NOT NULL,
                        away_score SMALLINT NOT NULL)"]))

(defn gen-fake-row []
  {:time (t/sql-timestamp (t/with-zone (t/zoned-date-time) "UTC"))
   :sportsbook "westgate"
   :matchup "NYY-BOS"
   :home_line (rand-nth [-110 110 130 -130])
   :away_line (rand-nth [-110 110 130 -130])
   :home_score (rand-nth [0 1 2 3])
   :away_score (rand-nth [0 1 2 3])})

(defn insert-dummy []
  (sql/insert! db :odds (gen-fake-row)))

(defn check-odds []
  (jdbc/execute! db [" SELECT * from odds; "]))

(defn -main
  [& args]
  (create-odds-table)
  (insert-dummy)
  (prn (check-odds)))
