(ns sportsball.slack
  (:require [clj-http.client :as client]
            [jsonista.core :as json]
            [clojure.string :as str]
            [sportsball.sb-specs :as specs]))

(defn env-var-set? [f env-var]
  (if (nil? f)
    (throw (Exception. (format "%s env var not set, see slack.clj" env-var)))
    f))

(defn get-env-var [env-var]
  (-> (System/getenv env-var)
      (env-var-set? env-var)
      slurp
      str/trim))

(def slack-url (get-env-var "SPORTSBALL_SLACK_POST_URL_FILE"))

(def slack-bot-auth-token (get-env-var "SPORTSBALL_SLACK_BOT_TOKEN"))

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

(defn post-simple-msg [msg]
  "Post a messge using the pre generated slack url. This can only be used
   for simple text messages and can't be used for interactive messages"
  (client/post slack-url
               {:body (json/write-value-as-string msg)
                :headers {}
                :content-type :json
                :accept :json}))

(defn post-msg
  ([body]
   (post-msg "https://slack.com/api/chat.postMessage" body))
  ([url body]
   "Post an interactive message in the sports channel"
   (client/post url
                {:body (json/write-value-as-string body)
                 :headers {}
                 :content-type :json
                 :accept :json
                 :oauth-token slack-bot-auth-token})))

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
    (format "Alert registered for the following game: %s
             Alert thresholds: %s, %s"
            game home-thres away-thres)))

(defn send-threshold-alert [book-prices matchup side]
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
     {:text
      (format "Odds threshold triggered for the %s matchup.
               Prices over threshold for %s at the following books:
               %s" matchup team current-prices)})))

(def alert-registration-msg
  {:channel sports-channel-id
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


(comment

  (post-msg alert-registration-msg)

  (post-simple-msg {:text "hello there"})

  )
