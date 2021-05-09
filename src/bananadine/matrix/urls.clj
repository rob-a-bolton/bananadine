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
  (:require [mount.core :refer [defstate start]]
            [bananadine.util :as util]
            [clojure.java.io :refer [as-url]]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as µ]
            [bananadine.matrix.events :refer [event-handler event-pub]])
  (:gen-class))

(declare start-url-handler stop-url-handler)

(def url-state (atom {}))

(def msg-chan (util/mk-chan))
(def url-chan (util/mk-chan))
(def urls-chan (util/mk-chan))
(def url-pub (pub url-chan :host))
(def urls-pub (pub urls-chan some?)) ; Publishes all urls

(defn publish-url
  [url event]
  (µ/log {:actual-event event})
  (let [out-evt (merge event {:host (.getHost url)
                              :url url})]
    (µ/log {:host (.getHost url) :url url})
    (go (>! url-chan out-evt)
        (>! urls-chan out-evt))))

(defn extract-urls
  [event]
  (let [urls (map as-url (re-seq #"http[s]?://[^/]+/[^ ]*"
                                 (get-in event [:msg :plain])))]
    (µ/log {:urls urls})
    (doall (map #(publish-url %1 event) urls))))

(util/setup-handlers
 url-handler
 url-state
 [[event-pub {:msg [msg-chan]}]]
 [[msg-chan extract-urls]])
