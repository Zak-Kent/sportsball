(ns sportsball.sb-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  (:import java.util.Date))

;; Books
(s/def ::odds (s/or :int int? :nil nil?)) ; s/nilable doesn't generate nils
(s/def ::home-odds ::odds)
(s/def ::away-odds ::odds)
(s/def ::book-odds (s/and
                    (s/keys :req [::home-odds ::away-odds])
                    (fn [{:keys [::home-odds ::away-odds]}]
                      ;; due to the s/or in ::odds home/away-odds are MapEntrys
                      (= (type (val home-odds)) (type (val away-odds))))))

(s/def ::bovada ::book-odds)
(s/def ::betonline ::book-odds)
(s/def ::bookmaker ::book-odds)
(s/def ::heritage ::book-odds)
(s/def ::intertops ::book-odds)
(s/def ::youwager ::book-odds)

(def team-abrv #{"ARI" "ATL" "BAL" "BOS" "CHC" "CWS" "CIN" "CLE" "COL"
                 "DET" "FLA" "HOU" "KAN" "LAA" "LAD" "MIL" "MIN" "NYM"
                 "NYY" "OAK" "PHI" "PIT" "SD" "SF" "SEA" "STL" "TB"
                 "TEX" "TOR" "WAS"})

(s/def ::team-id team-abrv)
(s/def ::home ::team-id)
(s/def ::away ::team-id)
(s/def ::teams (s/keys :req [::home ::away]))

(s/def ::timestamp inst?)

(s/def ::odds-info (s/keys :req [::teams ::timestamp ::bovada ::betonline
                                 ::bookmaker ::heritage ::intertops ::youwager]))

(s/conform ::odds-info {::timestamp (Date.)
                        ::teams {::home "ARI" ::away "TEX"}
                        ::bovada {::home-odds -155 ::away-odds 34}
                        ::heritage {::home-odds 55 ::away-odds 34}
                        ::betonline {::home-odds 55 ::away-odds nil}
                        ::bookmaker {::home-odds 55 ::away-odds 34}
                        ::intertops {::home-odds 55 ::away-odds 34}
                        ::youwager {::home-odds 55 ::away-odds 34}})

(gen/generate (s/gen ::odds-info))
