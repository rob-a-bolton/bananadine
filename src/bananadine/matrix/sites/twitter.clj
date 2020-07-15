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


(ns bananadine.matrix.sites.twitter
  (:require [mount.core :refer [defstate start]]
            [bananadine.db :as db]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-pub]]
            [bananadine.util :as util]
            [net.cgrand.enlive-html :as en]
            [clojure.java.io :refer [as-url]]
            [clojure.string :refer [join split]]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as µ])
  (:gen-class))

(def twitter-state (atom {}))
(def twitter-chan (util/mk-chan))

(defn twitter-key
  []
  (:twitterkey (db/get-server-info)))

(defn get-tweet-data
  [url]
  (µ/log {:try-url url})
  (let [post-id (last (split (.getPath url) #"/"))
        api-url "https://api.twitter.com/1.1/statuses/show.json"
        page (:body (client/get api-url
                                {:as :json
                                 :query-params {:id post-id}
                                 :oauth-token (twitter-key)}))]
    (µ/log {:page page})
    [:p
     (:text page)
     [:br]
     "~ " (get-in page [:user :screen_name])]))

(defn handle-link
  [event]
  (let [{:keys [channel sender host url]} event
        tweet-data (get-tweet-data url)]
    (µ/log tweet-data)
    (api/msg-room! channel tweet-data)))

(util/setup-handlers
 twitter-handler
 twitter-state
 [[url-pub {"twitter.com" [twitter-chan]
            "mobile.twitter.com" [twitter-chan]}]]
 twitter-chan
 handle-link)

