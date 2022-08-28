(ns sportsball.sb-specs
  (:require [malli.core :as m]
            [malli.generator :as mg]
            [malli.transform :as mt]
            [malli.error :as me]
            [java-time :as t]
            [clojure.string :as str])
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
                 [:fn (fn [{:keys [home-odds away-odds]}]
                        (= (type home-odds) (type away-odds)))]]))
(def book-keys [:bovada :betonline :bookmaker :heritage :intertops :youwager
                :Bet365 :Unibet :Betway :BetMGM :888sport])
(def books (m/schema
            [:map-of
             (into [] (cons :enum book-keys))
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
             [:fn (fn [{:keys [home-team away-team]}]
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

(defn decode-str->local-date [ds]
  (try
    (t/local-date "yyyy-MM-dd" ds)
    (catch Exception _
      ds)))

(def decode-local-date
  (m/schema [string? {:decode/string decode-str->local-date}]))

(def decode-date-range
  (m/schema [:map
             {:closed true}
             [:start decode-local-date]
             [:end decode-local-date]]))

(def converted-local-date
  (m/schema [:fn
             {:error/fn
              (fn [{:keys [value]} _]
                (format "Local date: %s could not be parsed.
                         Format should be 'yyyy-MM-dd'" value))}
             (fn [d] (= java.time.LocalDate (type d)))]))

(def converted-date-range
  (m/schema
   [:and [:map
          {:closed true}
          [:start converted-local-date]
          [:end converted-local-date]]
    [:fn
     {:error/fn
      (fn [{:keys [value]} _]
        (let [{:keys [start end]} value]
          (format "Start date: %s is after end date: %s"
                  (str start) (str end))))}
     (fn [{:keys [start end]}]
       (t/before? start end))]]))

(defn text->date-range [date-text]
  (let [dr-decoder (fn [dr]
                     (m/decode decode-date-range dr mt/string-transformer))]
    (->> (str/split date-text #" ")
         (zipmap [:start :end])
         dr-decoder)))

(def valid-date-range?
  (m/explainer converted-date-range))

(defn check-date-range [dr]
  (when-let [err (valid-date-range? dr)]
    (me/humanize err)))
