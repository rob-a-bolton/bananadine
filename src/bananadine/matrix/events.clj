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
            [bananadine.matrix.sync :refer [sync-state]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [com.brunobonacci.mulog :as mu]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [mount.core :refer [defstate]])
  (:gen-class))

(def event-atom (atom {}))

(def event-collection "events")

(defn upsert-event
  [channel event]
  (mu/log {:upserting event})
  (mc/update (:dbd db/dbcon)
              event-collection
              {:id (:event_id event) :channel channel}
              {$set event}
              {:upsert true}))

(defn get-event
  ([id]
   (mc/find-one-as-map (:dbd db/dbcon)
                       event-collection
                       {:id id}))
  ([channel id]
   (mc/find-one-as-map (:dbd db/dbcon)
                       event-collection
                       {:id id
                        :channel channel})))

(defn handle-invite-event
  [channel event]
  (mu/log {:invite-handle event})
  (let [user-id (db/get-user-id)
        sender (:sender event)]
    (when (and (= (:type event) "m.room.member")
               (= (get-in event [:content :membership]) "invite")
               (= (:state_key event) user-id)
               (not= sender user-id)
               sender)
      (mu/log {:msg-type :invite
               :channel channel
               :inviter sender})
      (util/run-hooks! event-atom
                       :invite
                       {:channel channel
                        :inviter sender}))))

(defn handle-invite-data
  [[channel data]]
  (doall (map #(handle-invite-event (name channel) %1)
              (get-in data [:invite_state :events]))))

(defn handle-leave-event
  [channel]
  (mu/log {:leave-handle channel})
  (util/run-hooks! event-atom
                   :leave
                   {:channel channel}))

(defn handle-leave-data
  [[channel _]]
  (handle-leave-event (name channel)))

(defn handle-room-event
  [channel event]
  (let [user-id (db/get-user-id)
        sender (:sender event)
        msg-type (:type event)]
    (mu/log :event :type msg-type :sender sender)
    (when-not (= sender user-id)
      (case msg-type
        "m.room.message"
          (util/run-hooks! event-atom
                       :room-msg
                       {:msg (util/extract-msg event)
                        :channel channel
                        :sender sender})
        "m.reaction"
          (util/run-hooks! event-atom
                       :msg-reaction
                       {:relates-to (get-in event [:m.relates_to :event_id])
                        :reaction (get-in event [:m.relates_to :key])
                        :channel channel
                        :sender sender})
        :unhandled))))

(defn handle-room-data
  [[channel data]]
  (doall (map #(handle-room-event channel %1)
              (get-in data [:timeline :events]))))

(defn get-id-events
  [syncdata]
  (let [join-leave-data (merge (get-in syncdata [:rooms :join])
                               (get-in syncdata [:rooms :leave]))
        f-extract-evts #(concat (get-in % [:state :events])
                                (get-in % [:timeline :events]))
        join-leave-evts (util/map-map f-extract-evts join-leave-data)
        invite-evts (util/map-map #(get-in % [:invite_state :events])
                                  (get-in syncdata [:rooms :invite]))]
    (merge join-leave-evts invite-evts)))

(defn handle-syncdata
  [data]
  (mu/log :evt-sync-handler :data data)
  (let [syncdata data
        join-events (get-in syncdata [:rooms :join])
        invite-events (get-in syncdata [:rooms :invite])
        leave-events (get-in syncdata [:rooms :leave])
        id-events (get-id-events syncdata)]
    (mu/log {:join-events (count join-events)
             :invite-events (count invite-events)
             :leave-events (count leave-events)})
    (doall (map (fn [[channel evts]]
                  (mu/log {:updating-evts-for channel :num-evts (count evts)})
                  ;; (doall (map #(mu/log {:channel channel :evt %}) evts)))
                  (doall (map (partial upsert-event channel) evts)))
                id-events))
    (doall (map handle-room-data join-events))
    (doall (map handle-invite-data invite-events))
    (doall (map handle-leave-data leave-events))))

(defn start-event-state!
  []
  (util/add-hook! sync-state :sync {:handler handle-syncdata})
  event-atom)

(defn stop-event-state!
  []
  (util/rm-hook! sync-state :sync handle-syncdata)
  (reset! event-atom {}))

(defstate event-state
  :start (start-event-state!)
  :stop (stop-event-state!))
