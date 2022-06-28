(ns sportsball.handlers
  (:require
   [ring.util.response :as rr]
   [sportsball.storage :as st]
   [sportsball.sb-specs :as spec]
   [java-time :as t]))

(defn store-odds [body]
  (let [odds (-> :body-params
                 body
                 (update :timestamp
                         (fn [x]
                           (when x (t/instant->sql-timestamp x)))))
        fmt-err (spec/check-odds odds)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (let [resp (st/store-odds odds)]
          (rr/response resp))))))
