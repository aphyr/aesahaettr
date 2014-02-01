(ns æsahættr
  "Provides functions for consistent hashing and partitioning of data." 
  (:refer-clojure :exclude [hash chunk])
  (:require [taoensso.nippy :as nippy])
  (:import (java.nio.charset Charset)
           (java.util BitSet)
           (com.google.common.primitives UnsignedBytes)
           (com.google.common.hash Hashing
                                   HashCode
                                   HashFunction
                                   Funnel
                                   PrimitiveSink)))

(defn common-bytes-prefix-size
  "Given two byte arrays, returns the number of bits in common."
  [^"[B" a ^"[B" b]
  (let [len (max (count a) (count b))
        ; Pad with trailing zeroes
        a (byte-array (concat a (repeat (- len (count a)) (byte 0))))
        b (byte-array (concat b (repeat (- len (count b)) (byte 0))))
        
        ; Convert to bigints
        a (BigInteger. 1 a)
        b (BigInteger. 1 b)

        ; Find differing bits
        diff (.xor a b)]

    ; Subtract the number of bits after the differing bit, from the total size.
    (- (* 8 len) (.bitLength diff))))

(declare bindump)

(defn byte-array-comparator
  "Comparator for byte arrays, lexicographically."
  []
  (UnsignedBytes/lexicographicalComparator))

(defn even-bytes-partitioner
  "Given a target number of subsets, a minimum key, and a maximum key, returns
  a function which maps byte arrays to an integer in 0..n, in lexicographic
  order. Useful if you want to keep nearby elements in the sequence in the
  same hash bucket."
  [^long n ^"[B" mink ^"[B" maxk]
  (assert (-> (UnsignedBytes/lexicographicalComparator)
              (.compare mink maxk)
              pos?
              not))

  (let [prefix        (common-bytes-prefix-size mink maxk)
        required-bits (long (/ (Math/log n) (Math/log 2)))]
;    (prn :prefix prefix :required-bits required-bits)
    (fn [^"[B" element]
;      (prn "bits are " (bindump element))
      (loop [i 0
             part 0]
        (if (<= required-bits i)
          part
          (if (let [i (+ prefix i)]
                (-> element
                    (aget (quot i 8))           ; Get byte in array
                    (bit-test (- 7 (mod i 8)))  ; Get bit in byte (reversed)
                    (try (catch ArrayIndexOutOfBoundsException e false))))
            (recur (inc i) ^long (bit-set part (- required-bits i 1)))
            (recur (inc i) part)))))))


(defn hexdump [byte-array]
  (apply str (map #(format "%02x" (bit-and 0xff %)) byte-array)))

(defn bindump [byte-array]
  (apply str (map (fn [x]
                    (subs (Integer/toBinaryString
                            (+ (bit-and 0xFF x)
                               0x100))
                          1))
                  byte-array)))

(defn consistent-hashcode
  "Maps a hashcode to an integer bucket."
  [buckets ^HashCode hashcode]
  (Hashing/consistentHash hashcode ^int buckets))

(defn consistent-long
  "Maps a long to an integer bucket."
  [buckets ^long hashcode]
  (Hashing/consistentHash hashcode ^int buckets))

(defn consistent
  "Maps a long or hashcode to an integer bucket. Uses reflection; use the
  consistent-hashcode or consistent-long functions for better performance."
  [buckets hashcode]
  (Hashing/consistentHash hashcode ^int buckets))

(defn hash->long
  "Converts a hashcode to a long."
  [^HashCode hashcode]
  (.asLong hashcode))

(defn hash->int
  "Converts a hashcode to an integer."
  [^HashCode hashcode]
  (.asInt hashcode))

(defn hash->bytes
  "Converts a hashcode to a byte array."
  [^HashCode hashcode]
  (.asBytes hashcode))

(defn md5
  "An MD5 hash function."
  []
  (Hashing/md5))

(defn murmur3-128
  "A murmur3 128-bit hash function."
  ([]     (Hashing/murmur3_128))
  ([seed] (Hashing/murmur3_128 seed)))

(defn murmur3-32
  "A murmur3 32-bit hash function."
  ([]     (Hashing/murmur3_32))
  ([seed] (Hashing/murmur3_32 seed)))

(defn hash-bytes
  "Applies a hash function to a byte array."
  [^HashFunction hash-function bytes]
  (.hashBytes hash-function bytes))

(def ^:static utf-16le (Charset/forName "UTF-16LE"))
(def ^:static utf-16be (Charset/forName "UTF-16BE"))
(def ^:static utf-16   (Charset/forName "UTF-16"))
(def ^:static utf-8    (Charset/forName "UTF-8"))

(defn ^HashCode hash-string
  "Applies a hash function to a character sequence in the given encoding. If no
  charset is provided, defaults to UTF-8, which benchmarks noticably faster for
  strings with a moderate mixture of ascii and multibyte chars. The 2-arity
  variant also calls .getBytes, instead of interpreting the string as a
  CharSequence, for significant performance savings."
  ([^HashFunction hash-function ^String string]
   ; Amazingly, using the string "UTF-8" instead of the charset is actually
   ; 16% *faster*.
   (.hashBytes hash-function (.getBytes string "UTF-8")))
  ([^HashFunction hash-function char-sequence charset]
   (.hashString hash-function char-sequence charset)))

(declare fast-funnel)

(defn hash-object
  "Hashes arbitrary objects using the given funnel, or Nippy if omitted.
  
  Note that nippy is *much* slower (~25x for a two-element map) than providing
  your own Funnel!"
  ([^HashFunction hash-function object]
   (.hashObject hash-function object fast-funnel))
  ([^HashFunction hash-function funnel object]
   (.hashObject hash-function object funnel)))

(def nippy-funnel
  "Allows the hashing of arbitrary data structures by serializing them with
  Nippy."
  (reify Funnel
    (^void funnel [_ obj ^PrimitiveSink sink]
      (.putBytes sink (nippy/freeze obj)))))

(defprotocol FastFunnel
  (fast-funnel! [obj sink]))

(def fast-funnel
  "Hashes sequences by simply writing each successive element to the underlying
  sink. This is an order of magnitude faster than Nippy, but can lead to a
  higher probability of hash collisions for specially structured collections.
  For example, [\"foo\" \"bar\"] and [\"f\" \"oobar\"] have the same hash."
  (reify Funnel
    (^void funnel [_ obj ^PrimitiveSink sink]
      (fast-funnel! obj sink))))

(extend-protocol FastFunnel
  Boolean               (fast-funnel! [x ^PrimitiveSink s] (.putBoolean s x))
  Short                 (fast-funnel! [x ^PrimitiveSink s] (.putShort s x))
  Integer               (fast-funnel! [x ^PrimitiveSink s] (.putInt s x))
  Long                  (fast-funnel! [x ^PrimitiveSink s] (.putLong s x))
  Byte                  (fast-funnel! [x ^PrimitiveSink s] (.putByte s x))
  String                (fast-funnel! [x ^PrimitiveSink s]
                          (.putBytes s (.getBytes x "UTF-8")))

  clojure.lang.Keyword  (fast-funnel! [x ^PrimitiveSink s]
                          (fast-funnel! (name x) s))
  
  clojure.lang.MapEntry (fast-funnel! [pair ^PrimitiveSink s]
                          (fast-funnel! (key pair) s)
                          (fast-funnel! (val pair) s))

  clojure.lang.IPersistentMap (fast-funnel! [m ^PrimitiveSink s]
                                (if (sorted? m)
                                  (fast-funnel! (seq m) s)
                                  ; Should use a commutative hash instead,
                                  ; but we don't know which hashfn is being
                                  ; used at this point. Need to thread it
                                  ; through the funnel calls, I guess.
                                  (fast-funnel! (sort m) s)))

  clojure.lang.IPersistentSet (fast-funnel! [set ^PrimitiveSink s]
                                (if (sorted? set)
                                  (fast-funnel! (seq set) s)
                                  (fast-funnel! (sort set) s)))

  clojure.lang.Sequential (fast-funnel! [xs ^PrimitiveSink s]
                            (doseq [x xs]
                              (fast-funnel! x s)))
  )

(extend-protocol FastFunnel
  (Class/forName "[B")  (fast-funnel! [x ^PrimitiveSink s] (.putBytes s x)))

(defn funnel-expr
  "Helper for the funnel macro. Given a sink symbol, a type symbol, and an
  expression, returns a form like (.putDouble sink expression)."
  [sink type expr]
  (if (= type 'string)
    (list '.putBytes sink (list '.getBytes
                                (vary-meta expr assoc :tag 'String)
                                "UTF-8"))
    (list (case type
            int   '.putInt
            long  '.putLong
            char  '.putChar
            byte  '.putByte
            bytes '.putBytes
            short '.putShort)
          sink
          expr)))

(defmacro funnel
  "Declaratively defines a funnel which takes a single variable name which will
  be bound to the object being hashed, followed by an alternating sequence of
  type symbols and expressions which return the values to be written to the
  underlying PrimitiveSink. For instance:

  (funnel cat
    int    (:weight cat)
    string (:color cat))"
  [source & pairs]
  (let [sink (gensym 'sink)]
    `(reify Funnel
       (^void ~'funnel [funnel# ~source ^PrimitiveSink ~sink]
         ~@(map (partial apply funnel-expr sink) (partition 2 pairs))))))
