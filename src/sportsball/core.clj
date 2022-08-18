(ns sportsball.core
  (:require [sportsball.config :as config]
            [sportsball.app :as sbapp])
  (:gen-class))

(defn -main
  [& args]
  (sbapp/init-app (config/load-config)))
