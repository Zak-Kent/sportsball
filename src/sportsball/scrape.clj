(ns sportsball.scrape
  (:require
   [java-time :as t]
   [hickory.core :as h]
   [hickory.select :as hs]
   [clojure.string :as str]
   [sportsball.sb-specs :as specs]
   [flatland.ordered.set :refer [ordered-set]]
   [overtone.at-at :as at-at])
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
        (str "caught exception: " (.getMessage e)))
      (finally
        (prn "shutting down web driver")
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
      ;; TODO: add logging
      (prn e)
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
                first
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
                           (class-prefix-selector "finalScore"))
                          page)
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
                             (class-prefix-selector "eventMarketGridContainer"))
                            page)
        teams (->> page
                   (hs/select (hs/descendant
                               (class-prefix-selector "participants")
                               (class-prefix-selector "gradientContainer")))
                   (partition 2)
                   (map sbr-get-teams))
        books (->> page
                   (hs/select (hs/descendant
                               (class-prefix-selector "columnsContainer")
                               (hs/attr :alt)))
                   sbr-get-books
                   (apply ordered-set))
        book-odds (->> game-row
                       (map #(hs/select
                              (hs/descendant
                               (class-prefix-selector "columnsContainer")
                               (class-prefix-selector "oddsNumber")) %))
                       (map #(partition 2 %))
                       (map #(map sbr-get-book-odds %))
                       (map #(zipmap (map keyword books) %)))
        scores (sbr-get-game-scores page)
        ;; add nils for games which haven't yet started
        scores-w-nils (concat
                       scores
                       (repeat (- (count teams) (count scores)) nil))
        scrape-time (t/instant)
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

(defn scrape-sportsbookreview []
  (let [url "https://www.sportsbookreview.com/betting-odds/mlb-baseball/money-line/"
        odds-infos (-> url
                       (html->data
                        sportsbookreview->odds-infos))]
    (let [validation-errs  (filter (complement nil?)
                                    (map specs/check-odds odds-infos))]
      (if (seq validation-errs)
        (do
          ;; TODO: add logging
          (clojure.pprint/pprint validation-errs))
        odds-infos))))


;; scheduling

(def scrape-pool (at-at/mk-pool))

(defn schedule-scrape [scrape-fn interval]
  (at-at/interspaced interval scrape-fn scrape-pool))
