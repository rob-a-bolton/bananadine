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


(ns bananadine.core
  (:require [mount.core :refer [defstate start]]
            [com.brunobonacci.mulog :as μ]
            [bananadine.logger]
            [bananadine.matrix.connection :refer [conn]]
            [bananadine.matrix.sync :refer [syncer]]
            [bananadine.matrix.events :refer [event-handler]]
            [bananadine.matrix.invites :refer [invite-handler]])
  (:gen-class))

;(μ/start-publisher! {:type :console})
(μ/start-publisher!
 {:type :custom
  :fqn-function "bananadine.logger/pretty-publisher"
  :filename "/tmp/bananadine.edn"})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start)
  (println "Hello, World!"))

'(require '[bananadine.db :as db]
         '[bananadine.util :as util]
         '[bananadine.matrix.api :as api]
         '[bananadine.matrix.sync :as msync]
         '[bananadine.matrix.events :as mevents]
         '[bananadine.matrix.invites :as minvites]
         '[bananadine.matrix.mentions :as mentions]
         '[bananadine.matrix.connection :refer [conn]]
         '[bananadine.matrix.urls :as murls]
         '[bananadine.matrix.sites.tanukitunes :as tanuki]
         '[cheshire.core :refer :all]
         '[hiccup.core :as hc]
         '[clojure.pprint :refer [pprint]]
         '[pl.danieljanus.tagsoup :as tsoup]
         '[net.cgrand.enlive-html :as en]
         '[clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
         '[clj-http.client :as client]
         '[clojurewerkz.ogre.core :as o]
         '[com.brunobonacci.mulog :as µ]
         '[clojure.tools.namespace.repl :refer [refresh]]
         '[mount.core :as mount])
'(mount/start #'bananadine.matrix.connection/conn
              #'bananadine.matrix.sync/syncer
              #'bananadine.matrix.events/event-handler
              #'bananadine.matrix.invites/invite-handler
              #'bananadine.matrix.mentions/mention-handler
              #'bananadine.matrix.urls/url-handler
              #'bananadine.matrix.sites.tanukitunes/tanuki-handler
              #'bananadine.db/dbcon)

;; SELinux - remember for local testing w/ nginx
;; setsebool -P httpd_can_network_connect 1
