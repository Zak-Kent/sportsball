(ns sportsball.utils
  (:require
   [clojure.pprint :as pprint]))

(defn ppformat
  [& args]
  (with-out-str
    (apply pprint/pprint args)))
