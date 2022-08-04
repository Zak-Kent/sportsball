(ns sportsball.config
  (:require
   [aero.core :as aero]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn env-var-set? [f env-var]
  (if (nil? f)
    (throw (Exception. (format "%s env var not set" env-var)))
    f))

(defmethod aero/reader 'req-env
  [{:keys [profile] :as opts} tag value]
  (-> (aero/reader opts 'env value)
      (env-var-set? value)))

(defmethod aero/reader 'pgpass
  [{:keys [profile] :as opts} tag value]
  (-> (slurp value)
      (str/split #":")
      last
      str/trim-newline))

(defmethod aero/reader 'slack-config-file
  [{:keys [profile] :as opts} tag value]
  (-> value
      slurp
      str/trim))

(def CONFIG nil)

(defn set-config! [config]
  (alter-var-root (var CONFIG) (constantly config)))

(defn load-config []
  (set-config! (aero/read-config (io/resource "config.edn"))))
