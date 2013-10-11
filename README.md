# Æsahættr

> He gave a soft shudder. His eyes were closed. Then he said, "It's a knife. The subtle knife of Cittàgazze. You haven't heard of it, Marisa? Some people call it teleutaia makhaira, the last knife of all. Others call it Æsahættr."
>
> "What does it do, Carlo? Why is it special?"
>
> "Ah... It's the knife that will cut anything. Not even its makers knew what it could do. Nothing, no one, matter, spirit, angel, air--nothing is invulnerable to the subtle knife."

Æsahættr supports consistent, stable hashing, oriented towards use in
probabilistic data structures and distributed systems. It wraps Guava's hashing
library in idiomatic Clojure, can (slowly) hash arbitrary Clojure data
structures, and provides a macro to define (much faster) custom hashing paths
as well.

Unlike the built-in java hashcode, Æsahættr's hashes are guaranteed to be
stable even between different machine architectures and JVMs, making them
suitable for use in distributed systems. Object.hashcode is 32 bits, and while
quite fast, often has poor dispersal. Æsahættr can generate hashes of
significantly higher entropy and with improved uniformity.

On the other hand, Æsahættr will probably be an order of magnitude slower than
.hashcode. Hashing an object like `{:weight 12 :color "blue"}` takes about 30
ns via .hashcode, but 370 ns via Æsahættr murmer3-32, with a custom funnel.
Hashing via nippy is another ~30x slower, at 12 µs. Still, we're within the
realm of acceptable performance for many purposes.

## Usage

```clojure
(use 'æsahættr)

; Hash a string using MD5. Strings are serialized as UTF-8 bytes, which should
; make it easy to get the same hash function between different languages and
; libraries.
(hash-string (md5) "æsahættr")
#<BytesHashCode 210ad27cce3cd17d413c6392e2e32c6f>

; Or, hash a byte array using murmur3-128 with a particular seed:
(hash-bytes (murmur3-128 1234) (.getBytes "æsahættr"))
#<BytesHashCode 6b5b120e544b34540a822b6fa452365e>

; We can dump that hashcode to bytes, a long, or an int, possibly losing
; entropy in the conversion.
(hash->long *1)
9066094382428588577

; Or use (str) to get a hex dump:
(str (hash-string (md5) "æsahættr"))
210ad27cce3cd17d413c6392e2e32c6f

; Consistent hashing is a technique used to divide work into a small number of
; stable buckets. To map a hashcode into an integer bucket ∈ [0, 1024), use
; `consistent`:
(consistent 1024 (hash-string (murmur3-32) "foo"))
574

; To hash compound datatypes, Æsahættr will serialize the object to bytes using
; Nippy, and hash those bytes. This is pretty slow, but minimizes the chance of
; collisions for variable-width structures with low entropy.
(hash-object (murmur3-32) {:weight 12 :color "blue"})
#<IntHashCode ea82010b>

; It's much faster to define a custom *funnel*, which writes primitive values
; extracted from an object into the bytestream for hashing. For instance, to
; hash cats by their weight and color:
(def cat-funnel (funnel cat
                 int    (:weight cat)
                 string (:color cat)))
(hash-object (murmur3-32) cat-funnel {:weight 12 :color "blue"})
#<IntHashCode 82ba126b>
```

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
