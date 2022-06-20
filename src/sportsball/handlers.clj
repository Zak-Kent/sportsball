(ns sportsball.handlers
  (:require
   [ring.util.response :as rr]
   [sportsball.storage :as st]))

(defn store-odds [body]
  (let [odds (:body-params body)]
    (rr/response odds)))
