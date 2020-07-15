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



(defproject bananadine "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "AGPLv3"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[cheshire "5.10.0"]
                 [clj-http "3.10.1"]
                 [clj-tagsoup/clj-tagsoup "0.3.0"
                  :exclusions [org.clojure/clojure
                               org.clojure/data.xml]]
                 [clojurewerkz/ogre "3.4.6.0"]
                 [com.brunobonacci/mulog "0.2.0"]
                 [com.grammarly/omniconf "0.4.1"]
                 [enlive "1.1.6"]
                 [hiccup "1.0.5"]
                 [mount "0.1.16"]
                 [org.apache.tinkerpop/neo4j-gremlin "3.4.6"]
                 [org.clojure/core.async "1.2.603"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/tools.cli "1.0.194"]
                 [org.clojure/tools.namespace "1.0.0"]
                 [org.neo4j/neo4j-tinkerpop-api-impl "0.7-3.2.3"]
                 [ring/ring-codec "1.1.2"]
                 [org.clojure/clojure "1.10.0"]]
  :main ^:skip-aot bananadine.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
