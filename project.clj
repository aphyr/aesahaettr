(defproject æsahættr "0.1.0-SNAPSHOT"
  :description "Sharding, partitioning, and consistent hashing. May release spectres."
  :url "http://github.com/aphyr/æsahættr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.google.guava/guava "15.0"]
                 [com.taoensso/nippy "2.1.0"]]
  :profiles {:dev {:dependencies [[criterium "0.4.1"]
                                  [reiddraper/simple-check "0.4.1"]]}})
