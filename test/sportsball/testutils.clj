(ns sportsball.testutils
  (:require  [clojure.test :as t]
             [clojure.string :as str]
             [next.jdbc :as jdbc]
             [sportsball.core :as sbcore]))

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
        (jdbc/execute! db-conn sbcore/odds-table-sql)))
    db-conf))

(defn call-with-test-db [f]
  (let [db-conf (build-test-db)]
    (with-open [conn (jdbc/get-connection
                       (jdbc/get-datasource db-conf))]
      (binding [*db-conn* conn]
        (f)))))

(defmacro with-test-db [& body]
    `(call-with-test-db (fn [] ~@body)))
