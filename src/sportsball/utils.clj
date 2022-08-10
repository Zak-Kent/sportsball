(ns sportsball.utils
  (:require
   [clojure.pprint :as pprint]))

(defn ppformat
  [& args]
  (with-out-str
    (apply pprint/pprint args)))

(defn csv->row-maps [header rows]
  (let [header-keys (map keyword header)
        convert-row (fn [row]
                      (zipmap header-keys row))]
    (map convert-row rows)))
