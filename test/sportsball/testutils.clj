(ns sportsball.testutils
  (:require [clojure.string :as str]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [java-time :as t]
            [jsonista.core :as j]
            [malli.core :as m]
            [malli.transform :as mt]
            [sportsball.sb-specs :as sbs]
            [sportsball.core :as sbc]))

(def ^:dynamic *db-conn* nil)

(defn db-config [db]
  {:dbtype "postgresql"
   :user "postgres"
   :port (-> (slurp "/Users/zakkent/Desktop/sportsball/box/port" )
             read-string)
   :password (-> (slurp "/Users/zakkent/Desktop/sportsball/box/pgpass")
                 (str/split #":")
                 last
                 str/trim-newline)})

(defn build-test-db []
  (let [db "sportsball_test"
        admin-conf (db-config db)
        db-conf (assoc admin-conf :dbname db)]
    (with-open [conn (jdbc/get-connection
                      (jdbc/get-datasource admin-conf))]
      (jdbc/execute! conn [(format "drop database if exists %s" db)])
      (jdbc/execute! conn [(format "create database %s" db)])
      (with-open [db-conn (jdbc/get-connection
                           (jdbc/get-datasource db-conf))]
        (jdbc/execute! db-conn sbc/odds-table-sql)))
    db-conf))

(defn call-with-test-db [f]
  (let [db-conf (build-test-db)]
    (with-open [conn (jdbc/get-connection
                       (jdbc/get-datasource db-conf))]
      (binding [*db-conn* conn]
        (f)))))

(defmacro with-test-db [& body]
  `(call-with-test-db (fn [] ~@body)))

(defn gen-fake-odds-info []
  {:time (t/sql-timestamp (t/with-zone (t/zoned-date-time) "UTC"))
   :sportsbook "westgate"
   :matchup "NYY-BOS"
   :home_line (rand-nth [-110 110 130 -130])
   :away_line (rand-nth [-110 110 130 -130])
   :home_score (rand-nth [0 1 2 3])
   :away_score (rand-nth [0 1 2 3])})

(defn gen-fake-odds-json []
  (j/write-value-as-string
   (m/encode
    sbs/odds-info
    (gen-fake-odds-info)
    mt/json-transformer)))
