(ns sportsball.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defmethod aero/reader 'pgpass
  [{:keys [profile] :as opts} tag value]
  (-> (slurp value)
      (str/split #":")
      last
      str/trim-newline))

(def CONFIG nil)

(defn set-config! [config]
  (alter-var-root (var CONFIG) (constantly config)))

(defn load-config []
  (set-config! (aero/read-config (io/resource "config.edn"))))
