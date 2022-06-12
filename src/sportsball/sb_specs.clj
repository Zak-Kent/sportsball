(ns sportsball.sb-specs
  (:require [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt])
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

(comment
  (m/validate odds-info
              {:timestamp (Date.)
               :game-score {:home-score 1 :away-score 3}
               :teams {:home-team "ARI" :away-team "TEX"}
               :bovada {:home-odds -155 :away-odds 34}
               :heritage {:home-odds 55 :away-odds 34}
               :betonline {:home-odds nil :away-odds nil}
               :bookmaker {:home-odds 55 :away-odds 34}
               :intertops {:home-odds 55 :away-odds 34}
               :youwager {:home-odds 55 :away-odds 34}})

  (mg/generate odds-info {:seed 55})

  )
