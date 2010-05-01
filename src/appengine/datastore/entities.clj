(ns appengine.datastore.entities
  (:import (com.google.appengine.api.datastore 
	    EntityNotFoundException Query Query$FilterOperator))
  (:require [appengine.datastore.core :as ds])
  (:use [clojure.contrib.str-utils2 :only (join)]
        appengine.utils))  ; FIXME add inflections - need to be updated to latest clojure 1.2 version.

(defn- pluralize [word] 
  "FIXME remove this defn and use inflections after updating to latest clojure 1.2 version." 
  word)

(defn- entity-key? [entity-specs]
  (let [[attribute & options] entity-specs]
    (= (:key (apply hash-map options)) true)))

(defn- entity-keys [entity-specs]
  (map first (filter #(entity-key? %) entity-specs)))

(defn- find-entities-fn-doc [entity property]
  (str "Find all " (pluralize (str entity)) " by " property "."))

(defn- find-entities-fn-name [entity property]
  (str "find-all-" (pluralize (str entity)) "-by-" property))

(defn- find-entity-fn-doc [entity property]
  (str "Find the first " entity " by " property "."))

(defn- find-entity-fn-name [entity property]
  (str "find-" entity "-by-" property))

(defn- filter-query [entity property value & [operator]]
  "Returns a query, where the value of the property matches using the
operator."
  (doto (Query. (str entity))
    (.addFilter
     (str property)
     (or operator Query$FilterOperator/EQUAL)
     (if (map? value) ((keyword value) value) value))))

(defn filter-fn [entity property & [operator]]
  "Returns a filter function that returns all entities, where the
property matches the operator."
  (let [operator (or operator Query$FilterOperator/EQUAL)]
    (fn [property-val]
      (ds/find-all
       (filter-query entity property property-val operator)))))

(defn- key-fn-name [entity]
  "Returns the name of the key builder fn for the given entity."
  (str "make-" (str entity) "-key"))

(defmacro def-key-fn [entity entity-keys & [parent]]
  "Defines a function to build a key from the entity-keys
propertoes. If entity-keys is empty the fn returns nil."
  (let [entity# entity entity-keys# entity-keys parent# parent]
    `(defn ~(symbol (key-fn-name entity#)) [~@(compact [parent#]) ~'attributes]
       ~(if-not (empty? entity-keys#)
          `(ds/create-key
            (:key ~parent#)
            ~(str entity#)
            (join "-" [~@(map #(list (if (keyword? %) % (keyword %)) 'attributes) entity-keys#)]))))))

(defmacro def-make-fn [entity & [parent]]
  "Defines a function to build entity hashes."
  (let [entity# entity parent# parent
        args# (compact [parent# 'attributes])]
    `(defn ~(symbol (str "make-" entity#)) [~@args#]
       (merge (assoc ~'attributes
		:kind ~(str entity#))
	      (let [key# (~(symbol (key-fn-name entity)) ~@args#)]
		(if-not (nil? key#) {:key key#} {}))))))

(defmacro def-create-fn [entity & [parent]]
  "Defines a function to build and save entity hashes."
  (let [entity# entity parent# parent
        args# (compact [parent# 'attributes])]
    `(defn ~(symbol (str "create-" entity#)) [~@args#]
       (ds/create-entity (~(symbol (str "make-" entity#)) ~@args#)))))

(defmacro deffilter [entity name doc-string [property operator] & [result-fn]]
  "Defines a finder function for the entity."
  (let [property# property]
    `(defn ~(symbol name) ~doc-string
       [~property#]
       (~(or result-fn 'identity)
        ((filter-fn '~entity '~property# ~operator) ~property#)))))

(defmacro def-find-all-by-property-fns [entity & properties]
  "Defines a function for each property, that find all entities by a property."
  (let [entity# entity]
    `(do
       ~@(for [property# properties]
           `(do
              (deffilter ~entity#
                ~(symbol (find-entities-fn-name entity# property#))
                ~(find-entities-fn-doc entity# property#)
                (~property#)))))))

(defmacro def-find-first-by-property-fns [entity & properties]
  "Defines a function for each property, that finds the first entitiy by a property."
  (let [entity# entity]
    `(do
       ~@(for [property# properties]
           `(do
              (deffilter ~entity#
                ~(symbol (find-entity-fn-name entity# property#))
                ~(find-entity-fn-doc entity# property#)
                (~property#) first))))))

(defmacro def-delete-fn [entity]
  "Defines a delete function for the entity."
  (let [entity# entity]
    `(defn ~(symbol (str "delete-" entity#)) [& ~'args]
       (ds/delete-entity ~'args))))

(defmacro def-find-all-fn [entity]
  "Defines a function that returns all entities."
  (let [entity# entity]
    `(defn ~(symbol (str "find-" (pluralize (str entity#)))) []
       (ds/find-all (Query. ~(str entity#))))))

(defmacro def-update-fn [entity]
  "Defines an update function for the entity."
  (let [entity# entity]
    `(defn ~(symbol (str "update-" entity#)) [~entity# ~'properties]
       (ds/update-entity ~entity ~'properties))))

(defmacro defentity [entity parent & properties]
  "Defines helper functions for the entity. Note that
   if no property is qualified by :key true, then the data
   store will create a unique key for this object.  However
   note that the result of calling make-*entity*-key for any 
   such object is nil and not a proper key."
  (let [entity# entity parent# parent properties# properties]
    `(do
       (def-key-fn ~entity# ~(entity-keys properties) ~@parent#)
       (def-make-fn ~entity# ~@parent#)
       (def-create-fn ~entity# ~@parent#)
       (def-delete-fn ~entity#)
       (def-find-all-by-property-fns ~entity# ~@(map first properties#))
       (def-find-all-fn ~entity#)
       (def-find-first-by-property-fns ~entity# ~@(map first properties#))
       (def-update-fn ~entity#))))
