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
            [sportsball.app :as sbapp]
            [sportsball.config :as config]
            [clojure.java.io :as io]
            [sportsball.metrics :as metrics]))

(defn db-config []
  {:dbtype "postgresql"
   :user "postgres"
   :port (-> (slurp "/Users/zakkent/Desktop/sportsball/box/port" )
             read-string)
   :password (-> (slurp "/Users/zakkent/Desktop/sportsball/box/pgpass")
                 (str/split #":")
                 last
                 str/trim-newline)})

(defn mock-app-config []
  {:db (db-config)
   :slack-conn-info {:url "foo" :bot-token "bar"}
   :alert-registry (atom {})
   :metrics (metrics/create-registry)})

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
        (store/init-db db-conn)))
    db-conf))

(defn call-with-test-config [f]
  (let [db-conf (build-test-db)]
    (f (assoc (mock-app-config) :db db-conf))))

(defn call-with-test-app-and-config [f]
  (let [test-config (call-with-test-config identity)]
    (f (sbapp/init-app-routes test-config) test-config)))

(defn gen-fake-odds-json []
  (j/write-value-as-string
   (m/encode
    sbs/odds-info
    (mg/generate sbs/odds-info {:seed 55})
    mt/json-transformer)))

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

(defn query-test-db [db query]
  (sql/query db [query]))

(defn all-matchups [{:keys [db]}]
  (query-test-db db "select count(*) from matchup"))

(defn all-odds [{:keys [db]}]
  (query-test-db db "select count(*) from odds"))
