(ns sportsball.core
  (:require [sportsball.storage :as st])
  (:gen-class))

(defn -main
  [& args]
  (st/create-odds-table))
