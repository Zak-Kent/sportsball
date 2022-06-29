(ns sportsball.handlers
  (:require
   [ring.util.response :as rr]
   [sportsball.storage :as st]
   [sportsball.sb-specs :as spec]
   [java-time :as t]))

(defn ts->sql-ts [ts]
  (when ts (t/instant->sql-timestamp ts)))

(defn store-odds [body]
  (let [odds (-> :body-params
                 body
                 (update :timestamp ts->sql-ts))
        fmt-err (spec/check-odds odds)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (let [resp (st/store-odds odds)]
          (rr/response resp))))))

(defn register-alert [body]
  (let [sub (-> :body-params
                body
                (update :timestamp ts->sql-ts))
        fmt-err (spec/check-alert-sub sub)]
    (if fmt-err
      (rr/bad-request fmt-err)
      (do
        (st/update-alerts sub)
        (rr/response sub)))))
