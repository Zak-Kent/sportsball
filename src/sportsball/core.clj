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
            [taoensso.timbre.appenders.core :as appenders])
  (:gen-class))

;; logging setup
(log/merge-config!
 {:appenders {:spit (appenders/spit-appender
                     {:fname "/Users/zakkent/Desktop/sportsball/log.txt"})}})
(log/set-level! :debug)

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

(defn -main
  [& args]
  (config/load-config)

  (try
    (scrape/schedule-scrape
     scrape/scrape-sportsbookreview
     ;; 5 mins in ms
     (* 1000 60 5))

    (jetty/run-jetty #'app-routes {:port 3000
                                   :join? false})

    (finally
      (at-at/stop-and-reset-pool! scrape/scrape-pool))))
