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

(ns bananadine.matrix.sync
  (:require [bananadine.db :as db]
            [bananadine.util :as util]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.connection :refer [conn]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub chan >! >!! <! <!! go]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))


(declare start-syncer! stop-syncer!)

(defstate syncer
  :start (start-syncer!)
  :stop (stop-syncer!))

(def sync-state (atom {}))
(def sync-chan (util/mk-chan))
(def sync-pub (pub sync-chan :msg-type))

(defn start-syncer!
  []
  (swap! sync-state assoc :running true)
  (go (while (:running @sync-state)
        (try
          (>! sync-chan {:msg-type :sync
                         :data (api/sync!)})
          (catch Exception e
            (µ/log (.getMessage e))))))
  sync-state)


(defn stop-syncer!
  []
  (swap! sync-state dissoc :running)
  sync-state)
