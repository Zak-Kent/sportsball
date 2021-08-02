(ns sportsball.core
  (:require [clojure.spec.alpha :as s])
  (:gen-class))

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

;; TODO replace this with a set of team names
(s/def :sb/team-id string?)
(s/def :sb/home :sb/team-id)
(s/def :sb/away :sb/team-id)
(s/def :sb/teams (s/keys :req [:sb/home :sb/away]))

;; input map of multiple books odds for one game
(s/def :sb/odds-info (s/keys :req [:sb/teams]
                             :opt [:sb/book-id]))

;; TODO add type for timestamp
(comment
  (s/conform :sb/odds-info {:sb/teams {:sb/home "foo" :sb/away "bar"}
                            :sb/bovada {:sb/home-odds -155 :sb/away-odds 34}
                            :sb/heritage {:sb/home-odds 55 :sb/away-odds 34}}))


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
