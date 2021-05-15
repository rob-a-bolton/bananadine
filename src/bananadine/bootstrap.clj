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


(ns bananadine.bootstrap
  (:require [bananadine.db :refer [dbcon]]
            [bananadine.matrix.connection :refer [conn]]
            [bananadine.matrix.events :refer [event-state]]
            ;;[bananadine.matrix.mentions :refer [mention-handler]]
            [bananadine.matrix.rooms :refer [room-state]]
            [bananadine.matrix.sites.generic :refer [generic-state]]
            [bananadine.matrix.sites.reddit :refer [reddit-state]]
            [bananadine.matrix.sites.tanukitunes :refer [tanuki-state]]
            [bananadine.matrix.sites.twitter :refer [twitter-state]]
            [bananadine.matrix.sites.youtube :refer [youtube-state]]
            [bananadine.matrix.sync :refer [sync-state]]
            [bananadine.matrix.urls :refer [url-state]])
  (:gen-class))

(def default-handlers
  [conn
   dbcon
   event-state
   generic-state
   ;;mention-state
   reddit-state
   room-state
   sync-state
   tanuki-state
   twitter-state
   url-state
   youtube-state])
