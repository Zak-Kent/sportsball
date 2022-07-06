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
            {:type "input"
             :label {:type "plain_text"
                     :text "Home team"}
             :element {:type "static_select"
                       :placeholder {:type "plain_text"
                                     :text "Select home team"}
                       :options (team-options)}}
            {:type "input"
             :label {:type "plain_text"
                     :text "Away team"}
             :element {:type "static_select"
                       :placeholder {:type "plain_text"
                                     :text "Select away team"}
                       :options (team-options)}}
            {:type "input"
             :label {:type "plain_text"
                     :text "Home threshold"}
             :hint {:type "plain_text"
                    :text "Enter notification threshold in American odds"}
             :element {:type "plain_text_input"
                       :action_id "home-threshold-select-input"}}
            {:type "input"
             :label {:type "plain_text"
                     :text "Away threshold"}
             :hint {:type "plain_text"
                    :text "Enter notification threshold in American odds"}
             :element {:type "plain_text_input"
                       :action_id "away-threshold-select-input"}}
            {:type "actions"
             :elements [{:type "button"
                         :text {:type "plain_text"
                                :text "Click to register alert"}
                         :value "foo"
                         :action_id "register-alert"}]}]})

(comment

  (slack-post-msg (slack-alert-registration-msg))

  (slack-post-simple-msg {:text "hello there"})

  )
