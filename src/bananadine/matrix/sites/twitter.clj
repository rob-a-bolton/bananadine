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
  (:require [mount.core :refer [defstate]]
            [bananadine.db :as db]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-state host-matcher]]
            [bananadine.util :as util]
            [clojure.string :as str]
            [clj-http.client :as client]
            [com.brunobonacci.mulog :as mu]
            [omniconf.core :as cfg])
  (:gen-class))

(def twitter-atom (atom {}))

(defn twitter-key
  []
  (cfg/get :twitterkey))

(defn get-tweet-data
  [url]
  (mu/log {:try-url url})
  (let [post-id (last (str/split (.getPath url) #"/"))
        api-url "https://api.twitter.com/1.1/statuses/show.json"
        page (:body (client/get api-url
                                {:as :json
                                 :query-params {:id post-id}
                                 :oauth-token (twitter-key)}))]
    (mu/log {:page page})
    [:p
     (:text page)
     [:br]
     "~ " (get-in page [:user :screen_name])]))

(defn handle-link
  [event]
  (let [{:keys [channel url]} event
        tweet-data (get-tweet-data url)]
    (mu/log tweet-data)
    (api/msg-room! channel tweet-data)))

(defn start-twitter-state!
  []
  (util/add-hook! url-state
                  :url
                  {:handler handle-link
                   :matcher (host-matcher ["twitter.com"])}))

(defn stop-twitter-state!
  []
  (util/rm-hook! url-state :url handle-link)
  (reset! twitter-atom {}))

(defstate twitter-state
  :start (start-twitter-state!)
  :stop (stop-twitter-state!))
