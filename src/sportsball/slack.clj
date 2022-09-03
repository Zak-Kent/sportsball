(ns sportsball.slack
  (:require [clj-http.client :as client]
            [jsonista.core :as json]
            [clojure.string :as str]
            [sportsball.sb-specs :as specs]
            [sportsball.config :as config]
            [clojure.java.io :as io]
            [sportsball.metrics :as metrics]))

(defn get-channel-id [{:keys [bot-token channel]}]
  (let [channels (-> (client/get "https://slack.com/api/conversations.list"
                                 {:oauth-token bot-token})
                     :body
                     (json/read-value json/keyword-keys-object-mapper))]
    (->> (:channels channels)
         (filter #(= channel (:name %)))
         first
         :id)))

(defn post-simple-msg [{:keys [url]} msg]
  "Post a messge using the pre generated slack url. This can only be used
   for simple text messages and can't be used for interactive messages"
  (client/post url
               {:body (json/write-value-as-string msg)
                :headers {}
                :content-type :json
                :accept :json}))

(defn post-msg
  ([config body]
   (post-msg config "https://slack.com/api/chat.postMessage" body))
  ([{:keys [bot-token]} url body]
   "Post an interactive message in the sports channel"
   (client/post url
                {:body (json/write-value-as-string body)
                 :headers {}
                 :content-type :json
                 :accept :json
                 :oauth-token bot-token})))

(defn team-options []
  "Helper func that makes the options vector for the team selection drop down"
  (let [make-option (fn [t] {:text {:type "plain_text" :text t}
                             :value t})]
    (->> specs/team-abrv
         rest
         (map make-option)
         (into []))))

(defn alert-sub->alert-reg-msg
  [{{home :home-team away :away-team} :teams
    {home-thres :home-threshold
     away-thres :away-threshold} :thresholds}]
  (let [game (format "%s-%s" home away)]
    (format "Alert registered for the following game: %s /n Alert thresholds: /n %s: %s, %s: %s"
            game home home-thres away away-thres)))

(defn send-threshold-alert [config book-prices matchup side]
  (let [current-prices (->> book-prices
                        (map (fn [[b p]]
                               (format "%s: %s\n" (name b) p)))
                        str/join)
        matchup (:matchup/teams matchup)
        team (-> matchup
                 (str/split #"-")
                 ((fn [[away home]]
                    (if (= side :home) home away))))]
    (post-simple-msg
     (:slack-conn-info config)
     {:text
      (format "Odds threshold triggered for the %s matchup.
               Prices over threshold for %s at the following books:
               %s" matchup team current-prices)})))

(defn alert-registration-msg [{:keys [channel-id]}]
  {:channel channel-id
   :text ""
   :blocks [{:type "header"
		         :text {:type "plain_text"
			              :text "Register an alert!"}}
            {:type "input"
             :block_id "home-team-select"
             :label {:type "plain_text"
                     :text "Home team"}
             :element {:type "static_select"
                       :placeholder {:type "plain_text"
                                     :text "Select home team"}
                       :options (team-options)
                       :action_id "home-team"}}
            {:type "input"
             :block_id "away-team-select"
             :label {:type "plain_text"
                     :text "Away team"}
             :element {:type "static_select"
                       :placeholder {:type "plain_text"
                                     :text "Select away team"}
                       :options (team-options)
                       :action_id "away-team"}}
            {:type "input"
             :block_id "home-threshold-select"
             :label {:type "plain_text"
                     :text "Home threshold"}
             :hint {:type "plain_text"
                    :text "Enter notification threshold in American odds"}
             :element {:type "plain_text_input"
                       :action_id "home-threshold"}}
            {:type "input"
             :block_id "away-threshold-select"
             :label {:type "plain_text"
                     :text "Away threshold"}
             :hint {:type "plain_text"
                    :text "Enter notification threshold in American odds"}
             :element {:type "plain_text_input"
                       :action_id "away-threshold"}}
            {:type "actions"
             :elements [{:type "button"
                         :text {:type "plain_text"
                                :text "Click to register alert"}
                         :value "foo"
                         :action_id "register-alert"}]}]})

(defn send-csv [{:keys [channel-id bot-token]} csv-stream]
  (with-open [csv-data (io/input-stream csv-stream)]
    (client/post "https://slack.com/api/files.upload"
                 {:multipart [{:name "filetype" :content "csv"}
                              {:name "file" :content csv-data}
                              {:name "channels" :content channel-id}]
                  :oauth-token bot-token})))

(defn send-health-check [{:keys [slack-conn-info]} health-msg]
  (post-simple-msg slack-conn-info {:text health-msg}))

(defn app-health-check [{:keys [metrics] :as config}]
  (let [health-msg (metrics/health-check-msg metrics)]
    (send-health-check config health-msg)))
