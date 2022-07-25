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
(def books (m/schema
            [:map-of
             [:enum :bovada :betonline :bookmaker :heritage :intertops :youwager
                    :Bet365 :Unibet :Betway :BetMGM :888sport]
             book-odds]))

;; Teams
(def team-abrv [:enum "ARI" "ATL" "BAL" "BOS" "CHC" "CWS" "CIN" "CLE" "COL"
                "DET" "FLA" "HOU" "KAN" "KC" "LAA" "LAD" "MIA" "MIL" "MIN" "NYM"
                "NYY" "OAK" "PHI" "PIT" "SD" "SF" "SEA" "STL" "TB" "TEX" "TOR"
                "WAS" "WSH"])
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
                 [:maybe [:map
                          {:closed true}
                          [:home-score home-score]
                          [:away-score away-score]]]))

(def timestamp (m/schema [inst?]))
(def odds-info (m/schema
                [:map
                 {:closed true}
                 [:teams teams]
                 [:timestamp timestamp]
                 [:books books]
                 [:game-score game-score]]))

(def valid-odds?
  (m/explainer odds-info))

(defn check-odds [odds]
  (when-let [err (valid-odds? odds)]
    (me/humanize err)))

;; Alerts
(def alert-thresholds
  (m/schema [:map
             {:closed true}
             [:home-threshold {:optional true} [:maybe int?]]
             [:away-threshold {:optional true} [:maybe int?]]]))

(def alert-sub
  [:map
   {:closed true}
   [:teams teams]
   [:thresholds alert-thresholds]
   [:timestamp timestamp]])

(def valid-alert-sub?
  (m/explainer alert-sub))

(defn check-alert-sub [sub]
  (when-let [err (valid-alert-sub? sub)]
    (me/humanize err)))
