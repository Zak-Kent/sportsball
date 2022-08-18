(ns sportsball.app
  (:require
   [integrant.core :as ig]
   [taoensso.timbre :as log]
   [taoensso.timbre.appenders.core :as appenders]
   [reitit.ring.middleware.muuntaja :as rrmm]
   [muuntaja.format.form :as mform]
   [muuntaja.core :as m]
   [reitit.ring :as ring]
   [ring.adapter.jetty :as jetty]
   [sportsball.config :as config]
   [sportsball.storage :as store]
   [sportsball.handlers :as han]
   [sportsball.slack :as slack]))

(defn init-app-routes [{:keys [db slack-conn-info alert-registry] :as config}]
  (ring/ring-handler
   (ring/router
    [["/odds"
      {:post {:summary "Stores an odds bundle."
              :handler (partial han/store-odds config)}}]
     ["/alert-sub"
      {:post {:summary "Creates an alert trigger on a game."
              :handler (partial han/register-alert alert-registry)}}]
     ["/slack-alert-sub"
      {:post {:summary "Creates an alert trigger on a game via slack msg."
              :handler (partial han/slack-register-alert config)}}]
     ["/slack-send-alert-msg"
      {:post {:summary "Sends a slack msg with an alert registration form"
              :handler (partial han/slack-send-alert-register-msg slack-conn-info)}}]
     ["/export-csv"
      {:post {:summary "Sends a csv export of the odds table to slack"
              :handler (partial han/slack-send-csv-export config)}}]]
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

(defmethod ig/init-key :storage/db [_ db]
  (store/init-db db)
  db)

(defmethod ig/init-key :app/logging [_ {:keys [log-file]}]
  (log/merge-config!
   {:appenders {:spit (appenders/spit-appender
                       {:fname log-file})}})
  (log/set-level! :debug))

(defmethod ig/init-key :slack/conn-info [_ slack-conn-info]
  (assoc slack-conn-info :channel-id
         (slack/get-channel-id slack-conn-info)))

(defmethod ig/init-key :app/jetty [_ config]
  (let [jetty-config (:jetty config)
        config-with-alert-reg (assoc config :alert-registry (atom {}))
        app-routes (init-app-routes config-with-alert-reg)]
    (jetty/run-jetty app-routes jetty-config)))

(defn init-app [config]
  (ig/init config))