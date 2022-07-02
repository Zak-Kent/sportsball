(ns sportsball.testutils
  (:require [clojure.string :as str]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [java-time :as t]
            [jsonista.core :as j]
            [malli.core :as m]
            [malli.transform :as mt]
            [sportsball.sb-specs :as sbs]
            [sportsball.storage :as store]
            [sportsball.core :as sbcore]))

(defn db-config []
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
        admin-conf (db-config)
        db-conf (assoc admin-conf :dbname db)]
    (with-open [conn (jdbc/get-connection
                      (jdbc/get-datasource admin-conf))]
      (jdbc/execute! conn [(format "drop database if exists %s" db)])
      (jdbc/execute! conn [(format "create database %s" db)])
      (with-open [db-conn (jdbc/get-connection
                           (jdbc/get-datasource db-conf))]
        (jdbc/execute! db-conn store/matchup-table-sql)
        (jdbc/execute! db-conn store/odds-table-sql)))
    db-conf))

(defn call-with-test-db [f]
  (let [db-conf (build-test-db)]
    (with-open [conn (jdbc/get-connection
                       (jdbc/get-datasource db-conf))]
      (binding [store/*db* conn]
        (f)))))

(defmacro with-test-db [& body]
  `(call-with-test-db (fn [] ~@body)))

(defn gen-fake-odds-json []
  (j/write-value-as-string
   (m/encode
    sbs/odds-info
    (mg/generate sbs/odds-info {:seed 55})
    mt/json-transformer)))

;; http test setup
(def ^:dynamic *app* nil)

(defn call-with-http-app
  "Builds an HTTP app and make it available as *app* during the
  execution of (f)."
  [f]
  (binding [*app* sbcore/app-routes]
    ;; atom holds state when tests are evaled multiple times in repl, gross
    (reset! store/alert-registry {})
    (with-test-db
      (f))))

(defmacro with-http-app
  [& body]
  `(call-with-http-app (fn [] ~@body)))
