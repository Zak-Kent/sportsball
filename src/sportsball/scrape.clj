(ns sportsball.scrape
  (:require
   [java-time :as t]
   [hickory.core :as h]
   [hickory.select :as hs]
   [clojure.string :as str]
   [sportsball.sb-specs :as specs]
   [sportsball.storage :as store]
   [sportsball.utils :as utils]
   [flatland.ordered.set :refer [ordered-set]]
   [taoensso.timbre :as log]
   [metrics.counters :as mcount])
  (:import
   (java.time Duration)
   (org.openqa.selenium WebDriver)
   (org.openqa.selenium.firefox FirefoxDriver)
   (org.openqa.selenium.support.ui WebDriverWait)))

(defn html->data [url extract-fn]
  (let [driver (new FirefoxDriver)
        _ (new WebDriverWait driver (t/duration 20 :seconds))]
    (System/setProperty "webdriver.firefox.driver" "/usr/local/bin/geckodriver")
    (try
      (.get driver url)
      (let [html (.getPageSource driver)]
        ;; need to wait for page to load, investigate why WebDriverWait isn't waiting
        (Thread/sleep 20000)
        (extract-fn html))
      (catch Exception e
        (log/error
         (str "caught exception: " (.getMessage e))))
      (finally
        (log/info "shutting down web driver")
        (.close driver)))))

(defn class-prefix-selector
  "A selector that matches the pattern seen in Hickory which takes a
   class name prefix and will select a HTML element if it has a class
   name with the given prefix"
  [prefix]
  (letfn [(parse-classes [class-str]
            (into #{} (str/split class-str #" ")))]
    (hs/attr :class (fn [x] (some
                             #(str/starts-with? % prefix)
                             (parse-classes x))))))

(defn str->int [s]
  (try
    (Integer/parseInt s)
    (catch java.lang.NumberFormatException e
      (log/debug e)
      nil)))


;; sportsbookreview specific scraping funcs

(defn sbr-get-teams [teams]
  (let [extract (fn [t] (-> t :content first :content first))]
    (->> teams
         (map extract)
         (apply (fn [away home]
                  {:away-team away
                   :home-team home})))))

(defn sbr-get-books [books]
  (->> books
       (map :attrs)
       (map :alt)
       (map (fn [s] (-> s
                        (str/split #" ")
                        first)))))

(defn sbr-get-book-odds [[away-odds home-odds]]
  (letfn [(extract [odds]
            (-> odds
                :content
                first
                :content
                second
                :content
                first))
          (d->nil [odd]
            (if (= odd "-")
              nil
              (str->int odd)))]
    {:home-odds (d->nil (extract home-odds))
     :away-odds (d->nil (extract away-odds))}))

(defn sbr-get-game-scores [page]
  (let [scores (hs/select (hs/descendant
                           (class-prefix-selector "GameRows_scores"))
                          page)
        ;; TODO check if cases when one teams score is 0 and the other
        ;; is nil matters, or is only an edge case when in first inning
        ;; of the game.
        build-game-score (fn [[a h]]
                           {:away-score a
                            :home-score h})
        get-score (fn [s]
                    (->> (:content s)
                         (map #(-> % :content first str->int))
                         build-game-score))]
    (map get-score scores)))

(defn sbr-drop-nil-odds [{:keys [books] :as odds-info}]
  (letfn [(drop-nil-odds [[book odds]]
            (let [odds-vals (vals odds)]
              (if (some nil? odds-vals)
                nil
                {book odds})))]
    (->> books
         (map drop-nil-odds)
         (apply merge)
         (assoc odds-info :books))))

(defn sportsbookreview->odds-infos
  "Takes html, uses hickory to parse the html into Clojure data, then extracts
   the odds for the games found on a sportsbookreview live MLB odds page."
  [html]
  (let [page (-> html h/parse h/as-hickory)
        game-row (hs/select (hs/descendant
                             (class-prefix-selector "GameRows_eventMarketGridContainer"))
                            page)
        teams (->> page
                   (hs/select (hs/descendant
                               (class-prefix-selector "GameRows_participants")
                               (class-prefix-selector "GameRows_gradientContainer")))
                   (partition 2)
                   (map sbr-get-teams))
        books (->> page
                   (hs/select (hs/descendant
                               (class-prefix-selector "OddsTable_columnsContainer")
                               (hs/attr :alt)))
                   sbr-get-books
                   (filter #(not= "" %))
                   (apply ordered-set))
        book-odds (->> game-row
                       (map #(hs/select
                              (hs/descendant
                               (class-prefix-selector "GameRows_columnsContainer")
                               (class-prefix-selector "OddsCells_oddsNumber")) %))
                       (map #(partition 2 %))
                       (map #(map sbr-get-book-odds %))
                       (map #(zipmap (map keyword books) %)))
        scores (sbr-get-game-scores page)
        ;; add nils for games which haven't yet started
        scores-w-nils (concat
                       scores
                       (repeat (- (count teams) (count scores)) nil))
        scrape-time (t/instant->sql-timestamp (t/instant))
        odds-infos (map (fn [[t o s]]
                          {:books o
                           :teams t
                           :game-score s
                           :timestamp scrape-time})
                        (map vector teams book-odds scores-w-nils))]

    (assert (= (count teams) (count book-odds))
            "Number of teams and book odds didn't match!")

    (->> odds-infos
         (map sbr-drop-nil-odds)
         (filter (fn [{:keys [books]}]
                   ((complement nil?) books))))))

(defn validate-and-store-odds-infos [config odds-infos]
  (let [validation-errs  (filter (complement nil?)
                                 (map specs/check-odds odds-infos))]
    (if (seq validation-errs)
      (log/error (utils/ppformat validation-errs))
      (do
        (let [odds-infos-count (count odds-infos)]
          (mcount/inc!
           (-> config :metrics :odds-infos-found)
           odds-infos-count)
          (log/debug
           (format "Attempting to store odds-info bundles for: %s games"
                   odds-infos-count)))
        (dorun (map (partial store/store-odds config) odds-infos))))))

(defn scrape-sportsbookreview
  ([config]
   (scrape-sportsbookreview config
                            sportsbookreview->odds-infos
                            (partial validate-and-store-odds-infos config)))
  ([config conversion-fn store-fn]
   (let [url "https://www.sportsbookreview.com/betting-odds/mlb-baseball/money-line/"
         odds-infos (html->data url conversion-fn)]
     (store-fn odds-infos))))

(defn trigger-sportsbookreview-scrape
  "Helper that allows a scrape to be triggered from the REPL. Takes a file path
   for the dst arg and stores the raw HTML from the scrape in that location"
  [dst]
  (let [html->dst (fn [html] (spit dst html))]
    (scrape-sportsbookreview {} identity html->dst)))
