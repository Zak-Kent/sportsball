(ns sportsball.handlers
  (:require
   [ring.util.response :as rr]
   [sportsball.storage :as st]
   [sportsball.sb-specs :as spec]
   [sportsball.slack :as slack]
   [sportsball.csv :as sbcsv]
   [java-time :as t]
   [jsonista.core :as json]))

(defn ts->sql-ts [ts]
  (when ts (t/instant->sql-timestamp ts)))

(defn store-odds [config body]
  (let [odds (-> :body-params
                 body
                 (update :timestamp ts->sql-ts))
        fmt-err (spec/check-odds odds)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (let [resp (st/store-odds config odds)]
          (rr/response resp))))))

(defn register-alert [alert-registry body]
  (let [sub (-> :body-params
                body
                (update :timestamp ts->sql-ts))
        fmt-err (spec/check-alert-sub sub)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (st/update-alerts alert-registry sub)
        (rr/response sub)))))

(defn get-team [slack-input home|away]
  (let [select-key (keyword (format "%s-team-select" home|away))
        team-key (keyword (format "%s-team" home|away))
        team (-> slack-input
                 select-key
                 team-key
                 :selected_option
                 :value)]
    {team-key team}))

(defn get-threshold [slack-input home|away]
  (let [select-key (keyword (format "%s-threshold-select" home|away))
        threshold-key (keyword (format "%s-threshold" home|away))
        threshold (-> slack-input
                      select-key
                      threshold-key
                      :value)]
    {threshold-key (when threshold
                     (Integer/parseInt threshold))}))

(defn trigger-alert-registration [{:keys [alert-registry] :as config} payload]
  (let [response_url (:response_url payload)
        form-vals (-> payload :state :values)
        teams (map (partial get-team form-vals) ["home" "away"])
        thresholds (map (partial get-threshold form-vals) ["home" "away"])
        alert-sub (-> {:teams (apply merge teams)
                       :thresholds (apply merge thresholds)}
                      (assoc :timestamp (ts->sql-ts (t/instant))))
        fmt-err (spec/check-alert-sub alert-sub)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        ;; only register the alert when the user presses the button in slack
        (st/update-alerts alert-registry alert-sub)
        (slack/post-msg
         (:slack-conn-info config)
         response_url
         {:text (slack/alert-sub->alert-reg-msg alert-sub)})
        (rr/response {:ok 0})))))

(defn slack-register-alert [config body]
  (let [payload (-> body
                    :body-params
                    :payload
                    (json/read-value json/keyword-keys-object-mapper))
        register-button? (= "register-alert"
                            (-> payload :actions first :action_id))]
    (if register-button?
      (trigger-alert-registration config payload)
      ;; if action is not register-alert button ack req and do nothing
      (rr/response {:ok 0}))))

(defn slack-send-alert-register-msg [slack-conn-info body]
  (let [cmd (-> :body-params
                body
                :command)]
    (if (= "/register-game-alert" cmd)
      (rr/response (slack/alert-registration-msg slack-conn-info))
      (rr/bad-request {:error "unexpected command"}))))

(defn slack-send-csv-export [config body]
  (let [{:keys [command text]} (select-keys (:body-params body) [:command :text])
        date-range (when (seq text) (spec/text->date-range text))
        fmt-err (when date-range (spec/check-date-range date-range))]
    (if (= "/export-csv" command)
      (if fmt-err
        (do
          (rr/bad-request fmt-err))
        (do
          (sbcsv/send-slack-csv config date-range)
          (rr/response {:text "sending csv export"})))
      (rr/bad-request {:error "unexpected command"}))))
