(defproject sportsball "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.github.seancorfield/next.jdbc "1.2.780"]
                 [org.postgresql/postgresql "42.3.6"]
                 [clojure.java-time "0.3.3"]
                 [metosin/malli "0.8.4"]
                 [metosin/jsonista "0.3.6"]
                 [metosin/reitit "0.5.18"]
                 [metosin/muuntaja-form "0.6.8"]
                 [ring/ring-jetty-adapter "1.9.5"]
                 [ring/ring-mock "0.4.0"]
                 [org.seleniumhq.selenium/selenium-java "4.3.0"]
                 [hickory "0.7.1"]
                 [org.flatland/ordered "1.15.10"]
                 [clj-http "3.12.3"]
                 [overtone/at-at "1.2.0"]
                 [com.taoensso/timbre "5.2.1"]
                 [aero "1.1.6"]]
  :main ^:skip-aot sportsball.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}
             :dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
