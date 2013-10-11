(ns æsahættr
  "Provides functions for consistent hashing and partitioning of data." 
  (:refer-clojure :exclude [hash])
  (:require [taoensso.nippy :as nippy])
  (:import (java.nio.charset Charset)
           (com.google.common.hash Hashing
                                   HashCode
                                   HashFunction
                                   Funnel
                                   PrimitiveSink)))

(defn consistent
  [buckets ^HashCode hashcode]
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

(defn hash-string
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

(def nippy-funnel
  "Allows the hashing of arbitrary data structures by serializing them with
  Nippy."
  (reify Funnel
    (^void funnel [_ obj ^PrimitiveSink sink]
      (.putBytes sink (nippy/freeze obj)))))

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

(defn hash-object
  "Hashes arbitrary objects using the given funnel, or Nippy if omitted.
  
  Note that nippy is *much* slower (~25x for a two-element map) than providing
  your own Funnel!"
  ([^HashFunction hash-function object]
   (.hashObject hash-function object nippy-funnel))
  ([^HashFunction hash-function funnel object]
   (.hashObject hash-function object funnel)))
