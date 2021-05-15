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
  (:require [mount.core :refer [defstate]]
            [bananadine.util :as util]
            [bananadine.matrix.events :refer [event-state]]
            [clojure.java.io :refer [as-url]]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as mu])
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

(defn start-url-state!
  []
  (util/add-hook! event-state
                  :room-msg
                  {:handler extract-urls})
  url-atom)

(defn stop-url-state!
  []
  (util/rm-hook! event-state :room-msg extract-urls)
  (reset! url-atom {}))

(defstate url-state
  :start (start-url-state!)
  :stop (stop-url-state!))
