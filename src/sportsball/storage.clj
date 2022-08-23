(ns sportsball.storage
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [java-time :as t]
            [clojure.string :as str]
            ;; needed to load json protocol extensions for jdbc-next
            [sportsball.json :as _]
            [sportsball.slack :as slack]
            [sportsball.config :as config]
            [sportsball.sb-specs :as spec]))

(def matchup-table-sql
  ["create table if not exists matchup (
      matchup_id SERIAL PRIMARY KEY,
      time TIMESTAMPTZ NOT NULL,
      teams VARCHAR NOT NULL)"])

(def odds-table-sql
  ["create table if not exists odds (
      time TIMESTAMPTZ NOT NULL,
      lines JSONB NOT NULL,
      home_score SMALLINT NOT NULL,
      away_score SMALLINT NOT NULL,
      matchup_id INTEGER NOT NULL,
      CONSTRAINT fk_matchup
        FOREIGN KEY (matchup_id)
          REFERENCES matchup(matchup_id))"])

(defn init-db [db]
  (jdbc/execute! db matchup-table-sql)
  (jdbc/execute! db odds-table-sql))

(defn db-reachable? [db]
  (try
    (:alive (first (jdbc/execute! db ["select true as alive"])))
    (catch org.postgresql.util.PSQLException  ex
      (if (= java.net.ConnectException  (type (ex-cause ex)))
        false
        (throw ex)))))

(defn game-info->matchup [game-info]
  (let [ts (:timestamp game-info)
        teams (->> (:teams game-info)
                     vals
                     (str/join "-"))]
    ;; the rows returned by jdbc are namespaced maps
    #:matchup{:time ts :teams teams}))

(defn get-local-date [d]
  (t/format :iso-local-date
            (t/local-date-time d (t/zone-id))))

(defn get-matchup-date [m]
  (-> (:matchup/time m) get-local-date))

(defn maybe-trigger [config
                     {:keys [home-threshold away-threshold]}
                     {:keys [home-odds away-odds]}
                     matchup]
  (let [slack-alert (partial slack/send-threshold-alert config)
        send-alert (fn [odds threshold side]
                     (when threshold
                       (some->
                        (filter (fn [[book price]]
                                  (when (and price (> price threshold)) true))
                                odds)
                        (slack-alert matchup side))))]
    (send-alert home-odds home-threshold :home)
    (send-alert away-odds away-threshold :away)))

(defn odds-alert [{:keys [alert-registry] :as config} odds]
  (let [matchup (-> odds
                    game-info->matchup
                    (update :matchup/time get-local-date))
        current-odds (-> odds :books)]
    (when-let [threshold (@alert-registry matchup)]
      (maybe-trigger config
                     threshold
                     (->> current-odds
                          (reduce (fn [acc [book {home :home-odds away :away-odds}]]
                                    (-> acc
                                        (assoc-in [:home-odds book] home)
                                        (assoc-in [:away-odds book] away)))
                                  {}))
                     matchup))))

(defn update-alerts [alert-registry alert-req]
  (let [mu (-> alert-req
               game-info->matchup
               (update :matchup/time get-local-date))
        threshold (:thresholds alert-req)]
    (swap! alert-registry assoc mu threshold)))

(defn check-matchup [db matchup]
  "Takes an incoming matchup and returns a seq of any existing
   matchups for the same date already in the DB"
  (let [db-matchups (sql/query
                     db
                     ["select * from matchup where teams = ?"
                      (:matchup/teams matchup)])
        in-matchup (get-matchup-date matchup)]
    (filter (fn [m] (= (get-matchup-date m) in-matchup)) db-matchups)))

(defn store-matchup [db odds]
  "Takes an incoming odds bundle decides whether or not to store
   a new matchup. Returns the matchup_id used as a FK in the odds
   table."
  (let [mup (game-info->matchup odds)
        existing-mup (check-matchup db mup)]
    (if (seq existing-mup)
      ;; TODO handle case if there are matching games on the same date
      ;; double headers, etc
      (:matchup/matchup_id (first existing-mup))
      (:matchup/matchup_id (sql/insert! db :matchup mup)))))

(defn store-odds [{:keys [db] :as config} odds]
  (let [nil->0 #(if (nil? %) 0 %)
        get-score (fn [score]
                    (-> odds :game-score score nil->0))
        home-score (get-score :home-score)
        away-score (get-score :away-score)
        ts (:timestamp odds)
        match-id (store-matchup db odds)
        lines (dissoc odds :teams :game-score :timestamp)
        odds-row {:home_score home-score
                  :away_score away-score
                  :time ts
                  :matchup_id match-id
                  :lines (with-meta lines {:pgtype "jsonb"})}]
    (odds-alert config odds)
    (sql/insert! db :odds odds-row)))
