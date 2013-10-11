(ns æsahættr-test
  (:require [clojure.test :refer :all]
            [æsahættr :refer :all]
            [criterium.core :as criterium]
            [simple-check.clojure-test :refer [defspec]]
            [simple-check.core :as sc]
            [simple-check.generators :as gen]
            [simple-check.properties :as prop])
  (:import java.nio.charset.Charset))

(deftest string-test
  (let [s "hello «ταБЬℓσ»"]
    (is (= -2117012680634787267
           (hash->long (hash-string (md5) s))
           (hash->long (hash-string (md5) s utf-8))
           (hash->long (hash-bytes (md5) (.getBytes s "UTF-8")))))))

;(deftest perf-test
;  (let [s "A string with odd chars «ταБЬℓσ»"]
;    (criterium/quick-bench (hash-string (murmur3-32) s))
;    (criterium/quick-bench (hash-bytes  (murmur3-32) (.getBytes s "UTF-8")))))

(defspec consistent-test
  1000
  (prop/for-all [s gen/string
                 i (gen/choose 1 1024)]
                (<= 0 (consistent i (hash-string (murmur3-32) s)) (dec i))))

(defspec hash-object-test
  1000
  (prop/for-all [o1 (gen/vector gen/string)
                 o2 (gen/vector gen/string)]
                (and (= (hash-object (md5) o1)
                        (hash-object (md5) o1))
                     (or (= o1 o2)
                         (not= (hash-object (md5) o1)
                               (hash-object (md5) o2))))))

(defspec funnel-test
  1000
  (let [f (funnel cat
                  int    (:weight cat)
                  string (:color cat))
        m (murmur3-128)]
    (prop/for-all [w1 gen/int
                   w2 gen/int
                   c1 gen/string
                   c2 gen/string]
                  (and (or (= c1 c2)
                           (not= (hash-object m f {:weight w1 :color c1})
                                 (hash-object m f {:weight w1 :color c2})))
                       (or (= w1 w2)
                           (not= (hash-object m f {:weight w1 :color c1})
                                 (hash-object m f {:weight w2 :color c1})))
                       (= (hash-object m f {:weight w1 :color c1})
                          (hash-object m f {:weight w1 :color c1}))))))
                        

(deftest hash-object-perf-test
  (let [f (funnel cat
                  int    (:weight cat)
                  string (:color cat))
        m (murmur3-32)
        o {:weight 12 :color "blue"}]
    (criterium/quick-bench (hash-object m o))
    (criterium/quick-bench (hash-object m f o))
    (criterium/quick-bench (hash o))))
