(ns sportsball.utils
  (:require
   [clojure.pprint :as pprint])
  (:import
   (java.util.concurrent ScheduledThreadPoolExecutor TimeUnit)))

(defn ppformat
  [& args]
  (with-out-str
    (apply pprint/pprint args)))

(defn csv->row-maps [header rows]
  (let [header-keys (map keyword header)
        convert-row (fn [row]
                      (zipmap header-keys row))]
    (map convert-row rows)))

(defn scheduler [core-threads]
  (doto (ScheduledThreadPoolExecutor. core-threads)
    (.setRemoveOnCancelPolicy true)
    (.setExecuteExistingDelayedTasksAfterShutdownPolicy false)))

(defn schedule-with-fixed-delay-in-mins [s f initial-delay delay]
  (.scheduleWithFixedDelay s f initial-delay delay TimeUnit/MINUTES))
