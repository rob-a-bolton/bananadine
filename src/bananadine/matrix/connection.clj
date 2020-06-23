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

(ns bananadine.matrix.connection
  (:require [bananadine.matrix.api :as api]
            [bananadine.db :as db]
            [mount.core :refer [defstate]])
  (:gen-class))

(declare create-conn
         destroy-conn)

(defstate conn
  :start (create-conn)
  :stop (destroy-conn conn))

(def conn-state (atom {}))

(defn create-conn
  []
  (api/login!)
  (swap! conn-state assoc :connected true)
  conn-state)

(defn destroy-conn
  [conn]
  (swap! conn-state dissoc :connected)
  conn-state)


