(ns sportsball.slack
  (:require [clj-http.client :as client]
            [jsonista.core :as json]
            [clojure.string :as str]))

(def slack-url
  (-> (System/getenv "SPORTSBALL_SLACK_POST_URL_FILE")
      slurp
      str/trim))

(defn slack-post [msg]
  (client/post slack-url
               {:body (json/write-value-as-string msg)
                :headers {}
                :content-type :json
                :accept :json}))

(comment

  (slack-post {:text "hello there"})

  )
