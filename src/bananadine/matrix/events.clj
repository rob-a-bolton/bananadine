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
            [bananadine.matrix.auth :as auth]
            [bananadine.matrix.connection :refer [conn make-url]]
            [bananadine.matrix.sync :refer [sync-pub]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub chan >! >!! <! <!! go]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))

(declare start-event-handler
         stop-event-handler)

(defstate event-handler
  :start (start-event-handler)
  :stop (stop-event-handler))

(def event-state (atom {}))
(def event-chan (chan))
(def event-pub (pub event-chan :msg-type))
(def invite-pub (pub event-chan #(= (:msg-type %1) :invite)))
(def sync-chan (chan))
(sub sync-pub :msg-type sync-chan)

(defn handle-invite-event
  [channel event]
  (let [user-id (:user_id (auth/get-account-details))]
    (when (and (= (:type event) "m.room.member")
               (= (get-in event [:content :membership]) "invite")
               (= (:state_key event) user-id))
      (go (>!! event-chan
               {:msg-type :invite 
                :channel channel
                :inviter (:sender event)})))))

(defn handle-invite-data
  [[channel data]]
  (doall (map #(handle-invite-event channel %1)
              (get-in data [:invite_state :events]))))

(defn handle-room-event
  [channel event]
  (go (>!! event-chan
           {:msg-type :room :event event})))

(defn handle-room-data
  [[channel data]]
  (doall (map #(handle-room-event channel %1)
              (get-in data [:invite_state :events]))))

(defn handle-syncdata
  [syncdata]
  (let [join-events (get-in syncdata [:rooms :join])
        invite-events (get-in syncdata [:rooms :join])]
    (doall (map handle-invite-data invite-events))
    (doall (map handle-invite-data invite-events))))
     

(defn start-event-handler
  []
  (swap! event-state assoc :running true)
  (go (while (:running @event-state)
        (let [syncdata (<! sync-chan)]
          (µ/log syncdata)
          (handle-syncdata syncdata)))))

(defn stop-event-handler
  []
  (swap! event-state dissoc :running))
