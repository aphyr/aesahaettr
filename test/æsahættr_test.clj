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

(defspec consistent-hashcode-test
  100
  (prop/for-all [s gen/string
                 i (gen/choose 1 1024)]
                (<= 0 (consistent-hashcode i (hash-string (murmur3-32) s)) (dec i))))

(defspec consistent-long-test
  100
  (prop/for-all [x gen/int
                 i (gen/choose 1 1024)]
                (<= 0 (consistent-long i x) (dec i))))

(defspec hash-object-test
  100
  (prop/for-all [o1 (gen/vector gen/int)
                 o2 (gen/vector gen/int)]
                (and (= (hash-object (murmur3-128) o1)
                        (hash-object (murmur3-128) o1))
                     (or (= o1 o2)
                         (not= (hash-object (murmur3-128) o1)
                               (hash-object (murmur3-128) o2))))))

(defspec funnel-test
  100
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

(let [byte-array-class (Class/forName "[B")]
  (defn unfucked=
    "Godfuckingdammit java"
    [a b]
    (if (instance? byte-array-class a)
      (java.util.Arrays/equals ^"[B" a ^"[B" b)
      (= a b))))

(defn hashes?
  "Does the given function hash x and y?"
  [h x y]
  (let [ok? (and (= (h x) (h x))
                 (= (h y) (h y))
                 (or (not= (h x) (h y))
                     (unfucked= x y)))]
      (when-not ok?
      (prn "Collision found?")
      (prn (str (h x)) x)
      (prn (str (h y)) y)
      (prn (= x y)))
    ok?))

(deftest fast-funnel-test
  ; Verify that values produced by the given generator are hashable using
  ; fast-funnel.
  (let [t (fn [generator]
            (let [r (sc/quick-check 100 (prop/for-all
                                          [x generator
                                           y generator]
                                          (hashes?
                                            (partial hash-object
                                                     (murmur3-128)
                                                     fast-funnel)
                                            x y)))]
              (when-not (:result r)
                (prn r))
              (:result r)))]
                
    (is (t gen/boolean))
    (is (t gen/byte))
    (is (t (gen/fmap short (gen/choose Short/MIN_VALUE Short/MAX_VALUE))))
    (is (t (gen/fmap int   gen/int)))
    (is (t gen/int))
    (is (t gen/bytes))
    (is (t gen/string))
    (is (t gen/keyword))
    (is (t (gen/list gen/int)))
    (is (t (gen/vector gen/keyword)))
    (testing "lazy seqs"
      (is (t (gen/fmap (partial map inc) (gen/list gen/int)))))
    (is (t (gen/fmap set (gen/list gen/int))))
    (is (t (gen/map gen/keyword gen/string)))
    ))

(deftest fast-funnel-order
  (let [h (partial hash-object (murmur3-128) fast-funnel)]
    (testing "maps"
      (is (= (h (array-map 1 2 3 4))
             (h (array-map 3 4 1 2)))))

    (testing "sets"
      ; These strings collide by JVM hashcode, so they'll retain insertion
      ; order in the hashset.
      (is (= (h (hash-set "Ea" "FB"))
             (h (hash-set "FB" "Ea")))))))

;(deftest hash-object-perf-test
;  (let [f (funnel cat
;                  int    (:weight cat)
;                  string (:color cat))
;        m (murmur3-32)
;        o {:weight 12 :color "blue"}]

;    (prn "Nippy")
;    (criterium/quick-bench (hash-object m nippy-funnel o))

;    (prn "Fast")
;    (criterium/quick-bench (hash-object m fast-funnel o))

;    (prn "Custom")
;    (criterium/quick-bench (hash-object m f o))

;    (prn ".hashcode")
;    (criterium/quick-bench (hash o))))
