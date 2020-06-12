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
            [bananadine.matrix.connection :refer [conn make-url]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub chan >! >!! <! <!! go]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))


(declare start-syncer stop-syncer)

(defstate syncer
  :start (start-syncer)
  :stop (stop-syncer))

(def sync-state (atom {}))
(def sync-chan (chan))
(def sync-pub (pub sync-chan :msg-type))

(defn set-next-batch!
  [next-batch]
  (db/set-simple-p! :server "next-batch" next-batch))

(defn get-next-batch
  []
  (db/get-simple-p :server "next-batch"))

(defn get-sync-args
  []
  (let [next-batch (get-next-batch)]
    (merge {}
           (if next-batch
             {:since next-batch
              :timeout 55000}))))

(defn clear-next-batch
  []
  (o/traverse @db/g
              (o/V)
              (o/has-label :server)
              (o/properties "next-batch")
              (o/drop)))

(defn do-sync!
  []
  (client/get 
   (make-url "_matrix/client/r0/sync")
   {:async? true
    :as :json
    :query-params (get-sync-args)
    :oauth-token (:token @conn)}
   ; Success
   (fn [response]
     (when-let [next-batch (get-in response [:body :next_batch])]
       (set-next-batch! next-batch))
     (µ/log (:body response))
     (>!! sync-chan {:msg-type :sync
                     :data (:body response)})
     (when (:running @sync-state)
       (do-sync!)))
   ; Failure
   (fn [exception]
     (µ/log exception))))

(defn start-syncer
  []
  (swap! sync-state assoc :running true)
  (do-sync!)
  sync-state)

