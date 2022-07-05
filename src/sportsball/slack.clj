(ns sportsball.slack
  (:require [clj-http.client :as client]
            [jsonista.core :as json]
            [clojure.string :as str]
            [sportsball.sb-specs :as specs]))

(def slack-url
  (-> (System/getenv "SPORTSBALL_SLACK_POST_URL_FILE")
      slurp
      str/trim))

(def slack-bot-auth-token
  (-> (System/getenv "SPORTSBALL_SLACK_BOT_TOKEN")
      slurp
      str/trim))

(defn get-channel-id [channel]
  (let [channels (-> (client/get "https://slack.com/api/conversations.list"
                                 {:oauth-token slack-bot-auth-token})
                     :body
                     (json/read-value json/keyword-keys-object-mapper))]
    (->> (:channels channels)
         (filter #(= channel (:name %)))
         first
         :id)))

(def sports-channel-id (get-channel-id "sports"))

(defn slack-post-simple-msg [msg]
  "Post a messge using the pre generated slack url. This can only be used
   for simple text messages and can't be used for interactive messages"
  (client/post slack-url
               {:body (json/write-value-as-string msg)
                :headers {}
                :content-type :json
                :accept :json}))

(defn slack-post-msg [body]
  "Post an interactive message in the sports channel"
  (client/post "https://slack.com/api/chat.postMessage"
               {:body (json/write-value-as-string body)
                :headers {}
                :content-type :json
                :accept :json
                :oauth-token slack-bot-auth-token}))

(defn team-options []
  "Helper func that makes the options vector for the team selection drop down"
  (let [make-option (fn [t] {:text {:type "plain_text" :text t}
                             :value t})]
    (->> specs/team-abrv
         rest
         (map make-option)
         (into []))))

(defn slack-alert-registration-msg []
  {:channel sports-channel-id
   :text ""
   :blocks [{:type "header"
		         :text {:type "plain_text"
			              :text "Register an alert!"}}
            {:type "section"
             :text {:type "mrkdwn"
                    :text "Pick home team"}
             :accessory {:action_id "home-team-select"
                         :type "multi_static_select"
                         :max_selected_items 1
                         :placeholder {:type "plain_text"
                                       :text "Select home team"}
                         :options (team-options)}}
            {:type "section"
             :text {:type "mrkdwn"
                    :text "Pick away team"}
             :accessory {:action_id "away-team-select"
                         :type "multi_static_select"
                         :max_selected_items 1
                         :placeholder {:type "plain_text"
                                       :text "Select away team"}
                         :options (team-options)}}]})

(comment

  (slack-post-msg (slack-alert-registration-msg))

  (slack-post-simple-msg {:text "hello there"})

  )
