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
            [bananadine.matrix.sync]
            [bananadine.matrix.events]
            [bananadine.matrix.invites]
            [bananadine.matrix.auth :as auth])
  (:gen-class))

;(μ/start-publisher! {:type :console})
(μ/start-publisher! {:type :simple-file
                     :pretty? true
                     :filename "/tmp/bananadine.log"})

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start)
  (println "Hello, World!"))

'(require '[bananadine.db :as db]
         '[bananadine.matrix.auth :as auth]
         '[bananadine.matrix.sync :as msync]
         '[bananadine.matrix.events :as mevents]
         '[bananadine.matrix.invites :as minvites]
         '[bananadine.matrix.connection :refer [conn make-url]]
         '[cheshire.core :refer :all]
         '[clojure.core.async :refer [pub chan >! >!! <! <!! go]]
         '[clj-http.client :as client]
         '[clojurewerkz.ogre.core :as o]
         '[com.brunobonacci.mulog :as µ]
         '[mount.core :as mount])
'(mount/start #'bananadine.matrix.connection/conn
              #'bananadine.matrix.sync/syncer
              #'bananadine.matrix.events/event-handler
              #'bananadine.matrix.invites/invite-handler
              #'bananadine.db/dbcon)

;; SELinux - remember for local testing w/ nginx
;; setsebool -P httpd_can_network_connect 1
