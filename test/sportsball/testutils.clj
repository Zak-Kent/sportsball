(ns sportsball.testutils
  (:require [clojure.string :as str]
            [malli.generator :as mg]
            [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [java-time :as t]
            [jsonista.core :as j]
            [malli.core :as m]
            [malli.transform :as mt]
            [sportsball.sb-specs :as sbs]
            [sportsball.storage :as store]
            [sportsball.core :as sbcore]
            [clojure.java.io :as io]))

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

(def temp-dir "test/resources/tmp-test-files")

(defn delete-dir [dir]
  (letfn [(delete [file]
            (when (.isDirectory file)
              (doseq [child (.listFiles file)]
                (delete child)))
            (io/delete-file file true))]
    (delete (io/file dir))))

(defn call-with-temp-files [f]
  (.mkdirs (io/file temp-dir))
  (let [result (f)]
    (delete-dir temp-dir)
    result))

(defmacro with-temp-files
  [& body]
  `(call-with-temp-files (fn [] ~@body)))

(defn query-test-db [query]
  (sql/query store/*db* [query]))

(defn all-matchups []
  (query-test-db "select count(*) from matchup"))

(defn all-odds []
  (query-test-db "select count(*) from odds"))
