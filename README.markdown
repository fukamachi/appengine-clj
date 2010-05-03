# Clojure library for Google App Engine / clojure-1.2 branch

* [fukamachi / appengine-clj](http://github.com/fukamachi/appengine-clj) forked
* created a clojure-1.2 branch
* modified datastore/entities.clj for latest clojure 1.2 version.

[Leiningen](http://github.com/technomancy/leiningen) usage:
    [org.clojars.fukamachi/appengine "0.2-SNAPSHOT"]

----

This library is a Clojure API for [Google App
Engine](http://code.google.com/appengine). It is based on John Hume's
[appengine-clj](http://github.com/duelinmarkers/appengine-clj) and has
some enhancements. It is heavily refactored, so don't expect
compatibility with the original library.

This version has wrapper functions for Memcache; added by [fukamachi](http://github.com/fukamachi)

### appengine.datastore.core

A convenience API for the
[com.google.appengine.api.datastore](http://code.google.com/appengine/docs/java/javadoc/index.html?com/google/appengine/api/datastore/package-tree.html)
package providing access to Google's schema-free datastore. It allows
you to work with immutable data structures instead of mutable Entity
instances.

Example:

<pre><code>
(create-entity {:kind "continent" :name "Europe"})
=> {:kind "continent" :key #&lt;Key Person(1138)&gt; :name "Europe"}
</code></pre>

### appengine.datastore.entities

A convenience API for the appengine.datastore namespace. Entity helper
functions can be generated with the <code>defentity</code> macro.

Examples:

<pre><code>

(defentity continent ()
  (iso-3166-alpha-2 :key true)
  (name))

(defentity country (continent)
  (iso-3166-alpha-2 :key true)
  (iso-3166-alpha-3)
  (name))

(defentity region (country)
  (code :key true)
  (name))

(with-local-datastore
  (let [continent (create-continent {:name "Europe" :iso-3166-alpha-2 "eu"})
        country (create-country continent {:name "Spain" :iso-3166-alpha-2 "es"})
        region (create-region country {:name "Galicia" :code "SP58"})]
    (println continent)
    (println country)
    (println region)))

; this prints: 

{:name Europe, :iso-3166-alpha-2 eu, :kind continent, :key #&lt;Key continent("eu")&gt;}
{:name Spain, :iso-3166-alpha-2 es, :parent-key #&lt;Key continent("eu")&gt;, :kind country, :key #&lt;Key continent("eu")/country("es")&gt;}
{:code SP58, :name Galicia, :parent-key #&lt;Key continent("eu")/country("es")&gt;, :kind region, :key #&lt;Key continent("eu")/country("es")/region("SP58")&gt;}

</code></pre>

### appengine.datastore.transactions

Transaction and retry support based on AppEngine semantics (see [DatastoreService low-level API for details](http://code.google.com/appengine/docs/java/javadoc/com/google/appengine/api/datastore/DatastoreService.html)).

<code>(with-transaction ...)</code> executes its body in a transaction.  In case of a DatastoreFailureException or a ConcurrentModificationException, the body's execution is retried *transaction-retries* times.  Beware that if the retry-count is reached and an exception is thrown within the body of the transaction, the transaction is thrown out of the <code>(with-transaction...)</code>.

The transaction functionality works with both appengine.datastore.core and appengine.datastore.entities.

Examples:

<pre><code>;; Either creates both entities or neither if too many datastore exceptions.
;; Returns the second entity, as expected.
(with-transaction
  (let [parent (ds/create-entity {:kind "Person" :name "jane"})]
    (ds/create-entity {:kind "Person" :name "bob" 
	               :parent-key (:key parent)})))

;; You can set the number of retries to deviate from the default (4)
(with-retries 2 (with-transaction ... ))</code></pre>

Transactions automatically rollback as per the low-level API specs.  Additionally, appengine-clj supports manual rollback using <code>(rollback-transaction)</code>.  Within a <code>(with-transaction ...)</code>, you may check whether the current transaction is active through <code>(is-transaction-active?)</code>.  These can be used together to get consistent snapshots of parts of the datastore.

<pre><code>(with-transaction
  ...
  (if (and something-went-wrong (is-transaction-active?))
    (rollback-transaction)))</code></pre>

You can nest transactions when working with two entity groups, but each transaction's success is independent of the other.

<pre><code>(with-transaction ;; group1
  (let [parent-group1 (ds/create-entity {:kind "Person" :name "jane"})
        child-group1 (ds/create-entity {:kind "Child" :name "tamara"
	             		       	:parent-key (:key parent-group1)})]
    (with-transaction ;; nested group2
      (let [parent-group2 (ds/create-entity {:kind "Person" :name "berni"})
            child-group2 (ds/create-entity {:kind "Child" :name "eric"
	             		       	:parent-key (:key parent-group2)})]
        ...
	))))</code></pre>

You can execute datastore operations outside of the current transaction through <code>(without-transaction)</code>.  Note: <code>(without-transaction)</code> has no retry semantics and should typically be surrounded by a (try ... (catch ...)) so that errors do not affect the surrounding transaction.

<pre><code>(with-transaction
  ...
  (try {
    (without-transaction 
      (ds/create-entity {:kind "Person" :name "andy"}))
    (catch ...))
  ...
)</code></pre>

### appengine.users

Convenience API for the
[com.google.appengine.api.users](http://code.google.com/appengine/docs/java/javadoc/index.html?com/google/appengine/api/datastore/package-tree.html)
package.

The <code>user-info</code> function can be called from anywhere to get
the current user and a UserService.  For a more functional approach, a
Ring middlware function is provided to assoc the user info into every
request under the key :appengine-clj/user-info.



### appengine.test-utils

Provides setup and teardown of an in-memory Datastore for use in tests
or from a REPL.  If you're using <code>clojure.contrib.test-is</code>,
you can use the <code>dstest</code> macro to get a fresh Datastore for
each test.

---

Copyright (c) 2009, 2010 John D. Hume, Roman Scherer, Jean-Denis Greze.

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
