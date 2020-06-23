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
  (:require [mount.core :refer [defstate start]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-pub]]
            [bananadine.util :as util]
            [clojure.java.io :refer [as-url]]
            [clojure.string :refer [join]]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as µ])
  (:gen-class))

;; (declare start-tanuki-handler stop-tanuki-handler)

;; (defstate tanuki-handler
;;   :start (start-tanuki-handler)
;;   :stop (stop-tanuki-handler))

(def tanuki-state (atom {}))
(def tanuki-chan (util/mk-chan))

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

;; (defn handle-link
;;   [event]
;;   (µ/log {:tanuki event})
;;   (let [{:keys [channel sender host url]} event
;;         tanuki-data (get-tanuki-data url)]
;;     (µ/log tanuki-data)
;;     (api/msg-room! channel tanuki-data)))
(defn handle-link
  [event]
  (µ/log {:tanuki event}))
;; (defn start-tanuki-handler
;;   []
;;   (swap! tanuki-state assoc :running true)
;;   (go (while (:running @tanuki-state)
;;         (handle-link (<! tanuki-chan))))
;;   (sub url-pub "tanukitunes.com" tanuki-chan))

;; (defn stop-tanuki-handler
;;   []
;;   (swap! tanuki-state dissoc :running)
;;   (unsub url-pub "tanukitunes.com" tanuki-chan))

(util/setup-handlers
 tanuki-handler
 tanuki-state
 [[url-pub {"tanukitunes.com" [tanuki-chan]}]]
 tanuki-chan
 handle-link)

