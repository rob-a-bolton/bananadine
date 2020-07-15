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
            [bananadine.matrix.events :refer [event-handler]]
            [bananadine.matrix.invites :refer [invite-handler]]
            [bananadine.matrix.mentions :refer [mention-handler]]
            [bananadine.matrix.sites.generic :refer [generic-handler]]
            [bananadine.matrix.sites.reddit :refer [reddit-handler]]
            [bananadine.matrix.sites.tanukitunes :refer [tanuki-handler]]
            [bananadine.matrix.sites.twitter :refer [twitter-handler]]
            [bananadine.matrix.sites.youtube :refer [youtube-handler]]
            [bananadine.matrix.sync :refer [syncer]]
            [bananadine.matrix.urls :refer [url-handler]])
  (:gen-class))

(def default-handlers
  [dbcon
   conn
   event-handler
   invite-handler
   mention-handler
   generic-handler
   reddit-handler
   tanuki-handler
   twitter-handler
   youtube-handler
   syncer
   url-handler])
