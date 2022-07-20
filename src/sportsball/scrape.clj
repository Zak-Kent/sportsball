(ns sportsball.scrape
  (:require
   [java-time :as t]
   [hickory.core :as h]
   [hickory.select :as hs]
   [clojure.string :as str])
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

(comment
  (let [url "https://www.sportsbookreview.com/betting-odds/mlb-baseball/money-line/"
        game-url "https://www.sportsbookreview.com/betting-odds/mlb-baseball/4677364/odds/"
        extract-func (fn [html] (spit "game-scrape.html" html))]

    (html->data game-url extract-func))

  )

(comment

  (let [f (slurp "game-scrape.html")
        page-tree (-> f
                      h/parse
                      h/as-hickory)]

    (count (hs/select (hs/descendant
                       (class-prefix-selector "sportbooks-1K"))
                      page-tree))
    )

  )
