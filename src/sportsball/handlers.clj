(ns sportsball.handlers
  (:require
   [ring.util.response :as rr]
   [sportsball.storage :as st]
   [sportsball.sb-specs :as spec]
   [sportsball.slack :as slack]
   [java-time :as t]
   [jsonista.core :as json]))

(defn ts->sql-ts [ts]
  (when ts (t/instant->sql-timestamp ts)))

(defn store-odds [body]
  (let [odds (-> :body-params
                 body
                 (update :timestamp ts->sql-ts))
        fmt-err (spec/check-odds odds)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (let [resp (st/store-odds odds)]
          (rr/response resp))))))

(defn register-alert [body]
  (let [sub (-> :body-params
                body
                (update :timestamp ts->sql-ts))
        fmt-err (spec/check-alert-sub sub)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (st/update-alerts sub)
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

(defn slack-register-alert [body]
  (let [form-vals (-> :body-params
                      body
                      :payload
                      (json/read-value json/keyword-keys-object-mapper)
                      :state
                      :values)
        teams (map (partial get-team form-vals) ["home" "away"])
        thresholds (map (partial get-threshold form-vals) ["home" "away"])
        alert-sub (-> {:teams (apply merge teams)
                       :thresholds (apply merge thresholds)}
                      (assoc :timestamp (ts->sql-ts (t/instant))))
        fmt-err (spec/check-alert-sub alert-sub)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (st/update-alerts alert-sub)
        (rr/response {:ok 0})))))

(defn slack-send-alert-register-msg [body]
  (let [cmd (-> :body-params
                body
                :command)]
    (if (= "/register-game-alert" cmd)
      (rr/response slack/slack-alert-registration-msg)
      (rr/bad-request {:error "unexpected command"}))))

