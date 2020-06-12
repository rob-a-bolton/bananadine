;; This file is part of Bananadine

;; Bananadine is free software: you can redistribute it and/or modify
;; it under the terms of the GNU Affero General Public License as published by
;; the Free Software Foundation, either version 3 of the License, or
;; (at your option) any later version.

;; Bananadine is distributed in the hope that it will be useful,
;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;; GNU Affero General Public License for more details.

;; You should have received a copy of the GNU Affero General Public License
;; along with Bananadine.  If not, see <https://www.gnu.org/licenses/>.

(ns bananadine.db
  (:import [org.apache.tinkerpop.gremlin.structure Graph]
           [org.apache.tinkerpop.gremlin.structure.util.empty EmptyVertexProperty]
           [org.apache.tinkerpop.gremlin.neo4j.structure Neo4jGraph])
  (:require [bananadine.util :refer [blank-str-array]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as μ]
            [mount.core :refer [defstate]])
  (:gen-class))


(declare connect-db disconnect-db)

(defstate dbcon
  :start (connect-db)
  :stop (disconnect-db))

(def graph (atom nil))
(def g (atom nil))

(defn connect-db
  []
  (reset! graph (o/open-graph {(Graph/GRAPH) (.getName Neo4jGraph)
                               "gremlin.neo4j.directory" "/var/db/neo4j"}))
  (reset! g (o/traversal @graph))
  {:graph graph
   :g g})

(defn commit-db
  []
  (.commit (.tx @g)))

(defn get-simple-v
  [label]
  (first (o/traverse @g (o/V)
                     (o/has-label label)
                     (o/into-list!))))

(defn get-prop
  [v p]
  (let [maybe-prop (.property v p)]
    (when-not (instance? EmptyVertexProperty maybe-prop)
      (.value maybe-prop))))

(defn get-props
  [v]
  (iterator-seq (.properties v (blank-str-array))))

(defn get-simple-p
  [label property]
  (let [v (get-simple-v label)]
    (when v
      (get-prop v property))))

(defn set-simple-p!
  [label property value]
  (let [v (get-simple-v label)]
    (.property v property value)
    (commit-db)
    v))

(defn make-server-entry
  [host]
  (o/traverse @g (o/addV :server)
                 (o/property :host host)
                 (o/next!))
  (commit-db))
