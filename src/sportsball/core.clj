(ns sportsball.core
  (:require [sportsball.config :as config]
            [sportsball.handlers :as han]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [reitit.ring.middleware.muuntaja :as rrmm]
            [muuntaja.core :as m]
            [muuntaja.format.form :as mform]
            [sportsball.scrape :as scrape]
            [overtone.at-at :as at-at]
            [taoensso.timbre :as log]
            [taoensso.timbre.appenders.core :as appenders]
            [sportsball.storage :as store])
  (:gen-class))

(defn setup-logging []
  (log/merge-config!
   {:appenders {:spit (appenders/spit-appender
                       {:fname (-> config/CONFIG :logging :log-file)})}})
  (log/set-level! :debug))

(def app-routes
  (ring/ring-handler
   (ring/router
    [["/odds"
      {:post {:summary "Stores an odds bundle."
              :handler han/store-odds}}]
     ["/alert-sub"
      {:post {:summary "Creates an alert trigger on a game."
              :handler han/register-alert}}]
     ["/slack-alert-sub"
      {:post {:summary "Creates an alert trigger on a game via slack msg."
              :handler han/slack-register-alert}}]
     ["/slack-send-alert-msg"
      {:post {:summary "Sends a slack msg with an alert registration form"
              :handler han/slack-send-alert-register-msg}}]]
    {:data {:muuntaja
            (m/create (-> m/default-options
                          (update :formats
                                  (fn [fmts]
                                    (assoc fmts
                                           "application/x-www-form-urlencoded"
                                           mform/format)))))
            :middleware [rrmm/format-middleware]}})
   (ring/create-default-handler
    {:not-found (constantly {:status 404 :body "Not found, where did it go?"})})))

(defn load-configs []
  ;; order matters here, the config must be loaded first
  (config/load-config)
  (setup-logging)
  (store/setup-db-config))

(defn -main
  [& args]
  (load-configs)

  (try
    (scrape/schedule-scrape
     scrape/scrape-sportsbookreview
     ;; 5 mins in ms
     (* 1000 60 5))

    (jetty/run-jetty #'app-routes {:port 3000
                                   :join? false})

    (finally
      (at-at/stop-and-reset-pool! scrape/scrape-pool))))
