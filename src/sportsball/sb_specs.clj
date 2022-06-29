(ns sportsball.sb-specs
  (:require [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [malli.error :as me])
  (:import java.util.Date))

;; Books
(def odds (m/schema [:maybe [:int {:min -400 :max 400}]]))
(def home-odds odds)
(def away-odds odds)
(def book-odds (m/schema
                [:and
                 [:map
                  {:closed true}
                  [:home-odds home-odds]
                  [:away-odds away-odds]]
                 [:fn (fn [{:keys [:home-odds :away-odds]}]
                        (= (type home-odds) (type away-odds)))]]))
(def bovada book-odds)
(def betonline book-odds)
(def bookmaker book-odds)
(def heritage book-odds)
(def intertops book-odds)
(def youwager book-odds)

;; Teams
(def team-abrv [:enum "ARI" "ATL" "BAL" "BOS" "CHC" "CWS" "CIN" "CLE" "COL"
                "DET" "FLA" "HOU" "KAN" "LAA" "LAD" "MIL" "MIN" "NYM" "NYY"
                "OAK" "PHI" "PIT" "SD" "SF" "SEA" "STL" "TB" "TEX" "TOR"
                "WAS"])
(def home-team (m/schema team-abrv))
(def away-team (m/schema team-abrv))
(def teams (m/schema
            [:and
             [:map
              {:closed true}
              [:home-team home-team]
              [:away-team away-team]]
             [:fn (fn [{:keys [:home-team :away-team]}]
                    (not= home-team away-team))]]))

;; Score
(def score (m/schema [:maybe nat-int?]))
(def home-score score)
(def away-score score)
(def game-score (m/schema
                 [:map
                  {:closed true}
                  [:home-score home-score]
                  [:away-score away-score]]))

(def timestamp (m/schema [inst?]))
(def odds-info (m/schema
                [:map
                 {:closed true}
                 [:teams teams]
                 [:timestamp timestamp]
                 [:bovada bovada]
                 [:betonline betonline]
                 [:bookmaker bookmaker]
                 [:heritage heritage]
                 [:intertops intertops]
                 [:youwager youwager]
                 [:game-score game-score]]))

(def valid-odds?
  (m/explainer odds-info))

(defn check-odds [odds]
  (when-let [err (valid-odds? odds)]
    (me/humanize err)))


;; Alerts
(def alert-threshold (m/schema [float?]))

(def alert-sub
  [:map
   {:closed true}
   [:teams teams]
   [:threshold alert-threshold]
   [:timestamp timestamp]])
