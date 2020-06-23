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

(ns bananadine.matrix.mentions
  (:require [bananadine.db :as db]
            [bananadine.util :refer [full-user->local-user mk-chan]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.connection :refer [conn]]
            [bananadine.matrix.events :refer [event-handler event-pub]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub chan >! >!! <! <!! go]]
            [clojure.string :refer [includes?]]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))

(def mention-state (atom {}))
(def msg-chan (mk-chan))
(sub event-pub :msg msg-chan)
(def mention-chan (mk-chan))
(def mention-pub (pub mention-chan :mention))

(declare start-mention-handler stop-mention-handler)

(defstate mention-handler
  :start (start-mention-handler)
  :stop (stop-mention-handler))

(defn get-mention-link
  []
  (format "https://matrix.to/#/%s" (:user_id (db/get-server-info))))

(defn get-mentions
  [msg]
  (let [link (get-mention-link)
        name (full-user->local-user (:user_id (db/get-server-info)))
        plain (:plain msg)
        html (:html msg)]
    (cond
      (and (= :a (ffirst html))
           (= (:href (second (first html)))
              link))
      {:link :first}
      (some #(and (map? %1)
                  (= (:href %1) link))
            (flatten html))
      {:link :mid}
      (includes? plain name)
      {:name :somewhere})))

(defn filter-and-publish
  [event]
  (let [mention (get-mentions (:msg event))]
    (when mention
      (µ/log {:mention mention
              :event event})
      (go (>! mention-chan {:mention mention
                            :event event})))))


(defn start-mention-handler
  []
  (swap! mention-state assoc :running true)
  (go (while (:running @mention-state)
        (filter-and-publish (<! msg-chan)))))


(defn stop-mention-handler
  []
  (swap! mention-state dissoc :running))
