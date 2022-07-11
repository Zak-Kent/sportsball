(ns sportsball.core
  (:require [sportsball.handlers :as han]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [reitit.ring.middleware.muuntaja :as rrmm]
            [muuntaja.core :as m]
            [muuntaja.format.form :as mform])
  (:gen-class))

(def app-routes
  (ring/ring-handler
   (ring/router
    [["/odds" {:post {:summary "Stores an odds bundle."
                      :handler han/store-odds}}]
     ["/alert-sub" {:post {:summary "Creates an alert trigger on a game."
                           :handler han/register-alert}}]]
    {:data {:muuntaja (m/create (-> m/default-options
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
  (jetty/run-jetty #'app-routes {:port 3000
                                 :join? false}))
