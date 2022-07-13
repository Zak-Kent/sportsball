(ns sportsball.slack-testutils
  (:require [jsonista.core :as json]))

(def mock-slack-team-select-action
  [{:action_ts "1657749532.041733"
    :placeholder
    {:emoji true :type "plain_text" :text "Select away team"}
    :block_id "away-team-select"
    :type "static_select"
    :action_id "away-team"
    :selected_option
    {:value "ATL"
     :text {:emoji true :type "plain_text" :text "ATL"}}}])

(def mock-slack-register-alert-action
  [{:action_ts "1657749533.487249"
    :block_id "123"
    :value "foo"
    :type "button"
    :action_id "register-alert"
    :text
    {:emoji true
     :type "plain_text"
     :text "Click to register alert"}}])

(defn mock-slack-alert-action-msg [action]
  "Creates a map which mimics the structure of a slack msg sent when
   either a drop down option is selected or the register alert button
   is pressed"
  (let [act (case action
              :register-button mock-slack-register-alert-action
              :team-select mock-slack-team-select-action)]
    {:payload
     (json/write-value-as-string
      {:api_app_id "AOOO12345"
       :trigger_id
       "12345"
       :channel {:name "sports", :id "12345"}
       :type "block_actions"
       :state
       {:values
        {:away-team-select
         {:away-team
          {:type "static_select"
           :selected_option
           {:value "ATL"
            :text {:emoji true :type "plain_text" :text "ATL"}}}}
         :away-threshold-select
         {:away-threshold {:value nil :type "plain_text_input"}}
         :home-threshold-select
         {:home-threshold {:value "200" :type "plain_text_input"}}
         :home-team-select
         {:home-team
          {:type "static_select"
           :selected_option
           {:value "CHC"
            :text {:emoji true :type "plain_text" :text "CHC"}}}}}}
       :actions act
       :token "12345"
       :team {:id "12345" :domain "12345"}
       :container
       {:type "message"
        :is_ephemeral true
        :channel_id "12345"
        :message_ts "1657749325.000100"}
       :is_enterprise_install false
       :enterprise nil
       :user
       {:name "foo.bar"
        :username "foo.bar"
        :id "12345"
        :team_id "67859"}
       :response_url
       "https://foo.com"})}))
