(ns sportsball.scrape
  (:require
   [java-time :as t])
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

(comment
  (let [url "https://www.sportsbookreview.com/betting-odds/mlb-baseball/money-line/"
        extract-func (fn [html] (spit "test-scrape.html" html))]

    (html->data url extract-func))

  )
