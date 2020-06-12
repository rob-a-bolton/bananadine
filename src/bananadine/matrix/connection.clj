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
  (:require [mount.core :refer [defstate]]
            [bananadine.db :as db])
  (:gen-class))

(declare create-conn
         destroy-conn)

(defstate conn
  :start (create-conn)
  :stop (destroy-conn conn))

(defn create-conn
  []
  (atom {:host (db/get-simple-p :server "host")}))

(defn destroy-conn
  [conn]
  (reset! @conn {}))

(defn make-url
  [stub]
  (format "https://%s/%s" (:host @conn) stub))
