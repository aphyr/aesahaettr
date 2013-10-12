# Æsahættr

> He gave a soft shudder. His eyes were closed. Then he said, "It's a knife. The subtle knife of Cittàgazze. You haven't heard of it, Marisa? Some people call it teleutaia makhaira, the last knife of all. Others call it Æsahættr."
>
> "What does it do, Carlo? Why is it special?"
>
> "Ah... It's the knife that will cut anything. Not even its makers knew what it could do. Nothing, no one, matter, spirit, angel, air--nothing is invulnerable to the subtle knife."
>
> —Philip Pullman, "The Subtle Knife"

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

## Installation

https://clojars.org/aesahaettr

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

; Æsahættr knows how to hash the common Clojure datatypes, including lists,
; vectors, sets, maps, and lazy seqs.
(hash-object (murmur3-128) #{[1 2 {:foo 3}]})
#<BytesHashCode f1bb5c87662227499cb925317908db15>

; This is reasonably fast, but can cause collisions when collections contain
; elements whose sequential byte representation is identical.
user=> (hash-object (murmur3-128) ["a" "bcd"])
#<BytesHashCode 4fcd5646d6b77bb875e87360883e00f2>
user=> (hash-object (murmur3-128) ["ab" "cd"])
#<BytesHashCode 4fcd5646d6b77bb875e87360883e00f2>

; To hash compound datatypes, Æsahættr can serialize the object to bytes
; Nippy, and hash those bytes. This is an order of magnitude faster than the
default fast-funnel, but minimizes the chance of collisions for
variable-width structures with low entropy.
(hash-object (murmur3-32) nippy-funnel {:weight 12 :color "blue"})
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
## Performance

The builtin Java hashcode `(hash)` is the undisputed king, but has poor
dispersal. Next fastest are custom funnels, followed by the default
fast-funnel, followed by Nippy.

For the object {:weight 12 :color "blue"}, on a 2GHz i7-3537U, 4MB cache,
single-core throughput for hashing the same object is roughly:

.hashcode:    breaks criterium
custom:       ~ 3,500,000 hashes/sec/core
fast-funnel:  ~   270,000 hashes/sec/core
nippy-funnel: ~    84,000 hashes/sec/core

## License

Copyright © 2013 Kyle Kingsbury

Distributed under the Eclipse Public License, the same as Clojure.
