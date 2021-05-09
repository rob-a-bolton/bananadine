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


(ns bananadine.matrix.sites.youtube
  (:require [mount.core :refer [defstate start]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-pub]]
            [bananadine.util :as util]
            [cheshire.core :refer :all]
            [clojure.java.io :refer [as-url]]
            [clojure.string :refer [join split]]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [ring.util.codec :as rc]
            [com.brunobonacci.mulog :as µ])
  (:gen-class))

(def youtube-state (atom {}))
(def youtube-chan (util/mk-chan))

(defn to-stars
  [rating]
  (let [num-full (java.lang.Math/floor rating)
        num-empty (- 5 num-full)
        full-stars (repeat num-full "★")
        empty-stars (repeat num-empty "☆")]
    (join "" (concat full-stars empty-stars))))
  
(defn get-vid-info
  [vid-id]
  (let [raw (:body (client/get "https://youtube.com/get_video_info"
                               {:query-params {:video_id vid-id}}))
        form-encoded (rc/form-decode 
                      (clojure.string/replace raw #"\\u0026" "&"))
        player-res (parse-string (get form-encoded "player_response"))
        vid-details (get player-res "videoDetails")
        title (get vid-details "title")
        author (get vid-details "author")
        desc (get vid-details "shortDescription")
        tags (get vid-details "keywords")
        stars (get vid-details "averageRating")]
    [:p
     [:font {:color "#e28d69"} [:h2 title]]
     [:em author]
     [:br]
     [:font {:color "#f9c22b"} (to-stars stars)]
     [:br]
     [:pre [:code desc]]
     [:br]
     ;;[:font {:color "#999999"} [:em [:strong "Tags: "] (join ", " tags)]]
     ]))

(defn handle-link
  [event]
  (µ/log {:youtube event})
  (let [{:keys [channel sender host url]} event
        vid-id (get (util/decode-query-params (.getQuery url))
                    "v")
        vid-info (get-vid-info vid-id)]
    (api/msg-room! channel vid-info)))        

(util/setup-handlers
 youtube-handler
 youtube-state
 [[url-pub {"youtube.com" [youtube-chan]
            "www.youtube.com" [youtube-chan]}]]
 [[youtube-chan handle-link]])
