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

(ns bananadine.matrix.rooms
  (:import [com.mongodb MongoOptions ServerAddress])
  (:require [bananadine.db :as db]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.connection :refer [conn]]
            [bananadine.matrix.events :refer [event-state]]
            [bananadine.util :as util]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as µ]
            [monger.collection :as mc]
            [monger.core :as mg]
            [monger.credentials :as mcr]
            [monger.operators :refer :all]
            [mount.core :refer [defstate]])
  (:gen-class))


(def room-atom (atom {}))

(def room-collection "rooms")

(defn join-room
  [room-id & {:keys [data] :or {data {}}}]
  (mc/update (:dbd db/dbcon) room-collection
             {:id room-id}
             {$set (merge data {:active true})}
             {:upsert true}))

(defn leave-room
  [room-id & {:keys [data] :or {data {}}}]
  (mc/update (:dbd db/dbcon) room-collection
             {:id room-id}
             {$set (merge data {:active false})}
             {:upsert true}))

(defn update-room
  [room-id data]
  (mc/update (:dbd db/dbcon) room-collection
             {:id room-id}
             {$set data}
             {:upsert true}))

(defn get-room
  ([room-id]
   (mc/find-one-as-map (:dbd db/dbcon) room-collection
                       {:id room-id}))
  ([room-id ks]
   (let [ks (if (seqable? ks) ks [ks])]
     (get-in (get-room room-id) ks))))

(defn join-room-and-publish
  [room-id inviter]
  (µ/log {:joining room-id})
  (let [res (api/join-room room-id)]
    (join-room room-id :data {:invited-by inviter})
    (util/run-hooks! room-atom
                     :joined
                     {:room-id room-id
                      :inviter inviter
                      :res res})))

(defn handle-invite
  [event]
  (µ/log {:invite event})
  (join-room-and-publish (:channel event)
                         (:inviter event)))

(defn leave-room-and-publish
  [room-id]
  (µ/log {:left room-id})
  (leave-room room-id)
  (util/run-hooks! room-atom
                   :left
                   {:room-id room-id}))

(defn handle-leave
  [event]
  (µ/log {:leave event})
  (leave-room-and-publish (:channel event)))

(defn start-room-state!
  []
  (util/add-hook! event-state :invite {:handler handle-invite})
  (util/add-hook! event-state :leave {:handler handle-leave})
  room-atom)

(defn stop-room-state!
  []
  (util/rm-hook! event-state :invite handle-invite)
  (util/rm-hook! event-state :leave handle-leave)
  (reset! room-atom {}))

(defstate room-state
  :start (start-room-state!)
  :stop (stop-room-state!))
