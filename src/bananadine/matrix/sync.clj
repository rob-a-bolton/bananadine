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
            [com.brunobonacci.mulog :as mu]
            [mount.core :refer [defstate]])
  (:gen-class))

(def sync-atom (atom {}))

(defn sync-handler
  [sync-res]
  (try
    (util/run-hooks! sync-atom :sync sync-res)
    (catch Exception e
      (mu/log :exception :err (.getMessage e)))))

(defn sync-loop
  []
  (loop [sync-res (api/sync!)]
    (mu/log {:sync-loop sync-res})
    (when (:running @sync-atom)
      (mu/log (sync-handler sync-res))
      (recur (api/sync!)))))

(defn start-sync-state!
  []
  (swap! sync-atom assoc :running true)
  (future (sync-loop))
  sync-atom)


(defn stop-sync-state!
  []
  (reset! sync-atom {})
  sync-atom)

(defstate sync-state
  :start (start-sync-state!)
  :stop (stop-sync-state!))
