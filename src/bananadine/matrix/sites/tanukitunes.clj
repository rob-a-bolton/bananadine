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


(ns bananadine.matrix.sites.tanukitunes
  (:require [mount.core :refer [defstate]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-state host-matcher]]
            [bananadine.util :as util]
            [clj-http.client :as client]
            [com.brunobonacci.mulog :as mu])
  (:gen-class))

(def tanuki-atom (atom {}))

(defn album-link
  [album]
  [:a {:href (:id album)} (:name album)])

(defn artist-link
  [artist]
  [:a {:href (:id artist)} (:name artist)])

(defn get-tanuki-data
  [url]
  (let [data (:body (client/get (str url) {:accept :json :as :json}))]
    [:p
     [:font {:color "#e28d69"} [:h2 (:name data)]]
     [:font {:color "#e2c169"} (album-link (:album data))]
     [:br]
     [:font {:color "#b2e269"}
      (interpose ", " (map artist-link (:artists data)))]]))

(defn handle-link
  [event]
  (mu/log {:tanuki event})
  (let [{:keys [channel url]} event
        tanuki-data (get-tanuki-data url)]
    (mu/log tanuki-data)
    (api/msg-room! channel tanuki-data)))

(defn start-tanuki-state!
  []
  (util/add-hook! url-state
                  :url
                  {:handler handle-link
                   :matcher (host-matcher ["tanukitunes.com"])}))

(defn stop-tanuki-state!
  []
  (util/rm-hook! url-state :url handle-link)
  (reset! tanuki-atom {}))

(defstate tanuki-state
  :start (start-tanuki-state!)
  :stop (stop-tanuki-state!))
