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

(ns bananadine.matrix.events
  (:require [bananadine.db :as db]
            [bananadine.util :as util]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.connection :refer [conn]]
            [bananadine.matrix.sync :refer [syncer sync-pub]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))

(def event-state (atom {}))
(def event-chan (util/mk-chan))
(def event-pub (pub event-chan :msg-type))
(def sync-chan (util/mk-chan))

(defn handle-invite-event
  [channel event]
  (µ/log {:invite-handle event})
  (let [user-id (:user_id (db/get-server-info))]
    (when (and (= (:type event) "m.room.member")
               (= (get-in event [:content :membership]) "invite")
               (= (:state_key event) user-id)
               (not= (:sender event) user-id))
      (µ/log {:msg-type :invite
              :channel channel
              :sender (:sender event)})
      (go (>! event-chan
              {:msg-type :invite
               :channel channel
               :sender (:sender event)})))))

(defn handle-invite-data
  [[channel data]]
  (doall (map #(handle-invite-event (name channel) %1)
              (get-in data [:invite_state :events]))))

(defn handle-room-event
  [channel event]
  (let [user-id (:user_id (db/get-server-info))]
    (when (and (= (:type event) "m.room.message")
               (not= (:sender event) user-id))
      (µ/log {:msg-type :msg
              :msg (util/extract-msg event)})
      (go (>!! event-chan
               {:msg-type :msg
                :msg (util/extract-msg event)
                :channel channel
                :sender (:sender event)})))))

(defn handle-room-data
  [[channel data]]
  (doall (map #(handle-room-event channel %1)
              (get-in data [:timeline :events]))))

(defn handle-syncdata
  [data]
  (let [syncdata (:data data)]
    (let [join-events (get-in syncdata [:rooms :join])
          invite-events (get-in syncdata [:rooms :invite])]
      (µ/log {:join-events (count join-events)
              :invite-events (count invite-events)})
      (doall (map handle-room-data join-events))
      (doall (map handle-invite-data invite-events)))))

(util/setup-handlers
 event-handler
 event-state
 [[sync-pub {:sync [sync-chan]}]]
 sync-chan
 handle-syncdata)
