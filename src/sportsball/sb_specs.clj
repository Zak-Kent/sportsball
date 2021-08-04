(ns sportsball.sb-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import java.util.Date))

;; Books
(s/def :sb/odds int?)
(s/def :sb/home-odds :sb/odds)
(s/def :sb/away-odds :sb/odds)
(s/def :sb/book-odds (s/keys :req [:sb/home-odds :sb/away-odds]))

(s/def :sb/bovada :sb/book-odds)
(s/def :sb/betonline :sb/book-odds)
(s/def :sb/bookmaker :sb/book-odds)
(s/def :sb/heritage :sb/book-odds)
(s/def :sb/intertops :sb/book-odds)
(s/def :sb/youwager :sb/book-odds)
(s/def :sb/book-id #{:sb/bovada :sb/betonline :sb/bookmaker
                     :sb/heritage :sb/intertops :sb/youwager})

(def team-abrv #{"ARI" "ATL" "BAL" "BOS" "CHC" "CWS" "CIN" "CLE" "COL"
                 "DET" "FLA" "HOU" "KAN" "LAA" "LAD" "MIL" "MIN" "NYM"
                 "NYY" "OAK" "PHI" "PIT" "SD" "SF" "SEA" "STL" "TB"
                 "TEX" "TOR" "WAS"})

(s/def :sb/team-id team-abrv)
(s/def :sb/home :sb/team-id)
(s/def :sb/away :sb/team-id)
(s/def :sb/teams (s/keys :req [:sb/home :sb/away]))

(s/def :sb/timestamp inst?)

(s/def :sb/odds-info (s/keys :req [:sb/teams :sb/timestamp]
                             :opt [:sb/book-id]))

(s/conform :sb/odds-info {:sb/timestamp (Date.)
                          :sb/teams {:sb/home "ARI" :sb/away "TEX"}
                          :sb/bovada {:sb/home-odds -155 :sb/away-odds 34}
                          :sb/heritage {:sb/home-odds 55 :sb/away-odds 34}})

(gen/generate (s/gen :sb/odds-info))
