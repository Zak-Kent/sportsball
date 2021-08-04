(ns sportsball.sb-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import java.util.Date))

;; Books
(s/def ::odds int?)
(s/def ::home-odds ::odds)
(s/def ::away-odds ::odds)
(s/def ::book-odds (s/keys :req [::home-odds ::away-odds]))

(s/def ::bovada ::book-odds)
(s/def ::betonline ::book-odds)
(s/def ::bookmaker ::book-odds)
(s/def ::heritage ::book-odds)
(s/def ::intertops ::book-odds)
(s/def ::youwager ::book-odds)
(s/def ::book-id #{::bovada ::betonline ::bookmaker
                   ::heritage ::intertops ::youwager})

(def team-abrv #{"ARI" "ATL" "BAL" "BOS" "CHC" "CWS" "CIN" "CLE" "COL"
                 "DET" "FLA" "HOU" "KAN" "LAA" "LAD" "MIL" "MIN" "NYM"
                 "NYY" "OAK" "PHI" "PIT" "SD" "SF" "SEA" "STL" "TB"
                 "TEX" "TOR" "WAS"})

(s/def ::team-id team-abrv)
(s/def ::home ::team-id)
(s/def ::away ::team-id)
(s/def ::teams (s/keys :req [::home ::away]))

(s/def ::timestamp inst?)

(s/def ::odds-info (s/keys :req [::teams ::timestamp]
                           :opt [::book-id]))

(s/conform ::odds-info {::timestamp (Date.)
                        ::teams {::home "ARI" ::away "TEX"}
                        ::bovada {::home-odds -155 ::away-odds 34}
                        ::heritage {::home-odds 55 ::away-odds 34}})

(gen/generate (s/gen ::odds-info))
