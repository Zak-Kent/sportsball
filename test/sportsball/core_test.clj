(ns sportsball.core-test
  (:require [clojure.test :refer :all]
            [sportsball.testutils :as tu]
            [next.jdbc.sql :as sql]
            [sportsball.core :refer :all]))

(defn test-odds-insert []
  (sql/insert! tu/*db-conn* :odds (tu/gen-fake-odds-info)))

(defn query-test-db []
  (sql/query tu/*db-conn* ["select count(*) from odds"]))

(deftest insert-fake-row
  (tu/with-test-db
    (test-odds-insert)
    (is (= [{:count 1}] (query-test-db)))))
