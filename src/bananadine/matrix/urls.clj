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


(ns bananadine.matrix.urls
  (:require [bananadine.db :as db]
            [bananadine.util :as util]
            [bananadine.matrix.events :refer [event-state]]
            [bananadine.matrix.rooms :as rooms]
            [clojure.java.io :refer [as-url]]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as mu]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [mount.core :refer [defstate]])
  (:gen-class))


(def url-atom (atom {}))

(defn publish-url
  [url event]
  (mu/log {:actual-event event})
  (let [out-evt (merge event {:host (.getHost url)
                              :url url})]
    (mu/log {:host (.getHost url) :url url})
    (util/run-hooks! url-atom
                     :url
                     out-evt)))

(defn extract-urls
  [event]
  (let [urls (map as-url (re-seq #"http[s]?://[^/]+/[^ ]*"
                                 (get-in event [:msg :plain])))]
    (mu/log {:urls urls})
    (doall (map #(publish-url %1 event) urls))))

(defn host-matcher
  [hosts]
  (let [host-set (set hosts)]
    (fn [event] (contains? host-set (:host event)))))

(defn host-muted-for-channel?
  [channel mute-key]
  (let [mutes (set (rooms/get-room channel :mutes))]
    (contains? mutes mute-key)))

(defn polite-host-matcher
  [mute-key hosts]
  (let [h-matcher (host-matcher hosts)]
    (fn [event]
      (and (h-matcher event)
           (not (host-muted-for-channel? (:channel event)
                                         mute-key))))))

(defn mute-channel-generic
  [channel]
  (mc/update (:dbd db/dbcon)
             rooms/room-collection
             {:id channel}
             {$set {:no-generic true}}))

(defn unmute-channel-generic
  [channel]
  (mc/update (:dbd db/dbcon)
             rooms/room-collection
             {:id channel}
             {$unset {:no-generic true}}))

(defn mute-channel-flag
  [channel flag]
  (mc/update (:dbd db/dbcon)
             rooms/room-collection
             {:id channel}
             {$push {:mutes flag}}))

(defn unmute-channel-flag
  [channel flag]
  (mc/update (:dbd db/dbcon)
             rooms/room-collection
             {:id channel}
             {$pull {:mutes flag}}))

(defn start-url-state!
  []
  (util/add-hook! event-state
                  :m.text
                  {:handler extract-urls})
  url-atom)

(defn stop-url-state!
  []
  (util/rm-hook! event-state :m.text extract-urls)
  (reset! url-atom {}))

(defstate url-state
  :start (start-url-state!)
  :stop (stop-url-state!))
