(ns sportsball.core
  (:require [sportsball.handlers :as han]
            [reitit.ring :as ring]
            [ring.adapter.jetty :as jetty]
            [reitit.ring.middleware.muuntaja :as rrmm]
            [muuntaja.core :as m])
  (:gen-class))

(def app-routes
  (ring/ring-handler
   (ring/router
    [["/odds" {:post    {:summary "Stores an odds bundle."
                         :handler han/store-odds}
               :options (fn [_] {:status 200})}]]
    {:data {:muuntaja m/instance
            :middleware [rrmm/format-middleware]}})
   (ring/create-default-handler
    {:not-found (constantly {:status 404 :body "Not found, where did it go?"})})))

(defn -main
  [& args]
  (jetty/run-jetty #'app-routes {:port 3000
                                 :join? false}))
