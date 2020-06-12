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

(ns bananadine.matrix.invites
  (:require [bananadine.db :as db]
            [bananadine.matrix.connection :refer [conn make-url]]
            [bananadine.matrix.events :refer [invite-pub]]
            [bananadine.matrix.sync :refer [sync-pub]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub chan >! >!! <! <!! go]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))

(declare start-invite-handler
         stop-invite-handler)

(defstate invite-handler
  :start (start-invite-handler)
  :stop (stop-invite-handler))

(def invite-state (atom {}))

(def join-chan (chan))
(def join-pub (pub join-chan :joined))

(def invite-chan (chan))
(sub invite-pub :joined invite-chan)

(defn join-room
  [room-id inviter]
  (client/post
   (make-url (str "_matrix/client/r0/rooms/" room-id "/join"))
   {:async? true
    :as :json
    :oauth-token (:token @conn)}
   ; Success
   (fn [response]
     (>!! join-chan {:joined true
                     :channel room-id})
     (µ/log (:body response)))
   ; Failure
   (fn [exception]
     (µ/log exception))))

(defn start-invite-handler
  []
  (go (while (:running @invite-state)
        (let [event (<! invite-chan)]
          (join-room (:channel event)
                     (:inviter event)))))
  (swap! invite-state assoc :running true))

(defn stop-invite-handler
  []
  (swap! invite-state dissoc :running))

