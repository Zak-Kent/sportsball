(ns sportsball.core
  (:require [sportsball.config :as config]
            [sportsball.app :as sbapp]
            [integrant.core :as ig])
  (:gen-class))

(defn -main
  [& args]
  (let [system (sbapp/init-app (config/load-config))]
    (.addShutdownHook (Runtime/getRuntime)
                      (doto
                        (Thread. ^Runnable (fn []
                                             (ig/halt! system)
                                             (shutdown-agents)))
                        (.setName "Sportsball shutdown hook")))))
