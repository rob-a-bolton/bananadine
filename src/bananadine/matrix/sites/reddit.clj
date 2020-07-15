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


(ns bananadine.matrix.sites.reddit
  (:require [mount.core :refer [defstate start]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-pub]]
            [bananadine.util :as util]
            [cheshire.core :refer :all]
            [clojure.java.io :refer [as-url]]
            [clojure.string :refer [join split]]
            [clj-http.client :as client]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [net.cgrand.enlive-html :as en]
            [com.brunobonacci.mulog :as µ])
  (:gen-class))

(def reddit-state (atom {}))
(def reddit-chan (util/mk-chan))

(defn parse-post
  [post-elem]
  (map (fn [elem]
         (cond
           (re-matches #"[\n\r]+" elem)
             [:br]
           (re-matches #"http[s]?://[^/]+/[^ ]*" elem)
             [:a {:href elem}]
           :else elem))
       (en/texts post-elem)))

(defn get-post-info
  [post-path]
  (let [res (client/get (format "https://old.reddit.com%s" post-path)
                        {:headers {"User-Agent" util/user-agent}})
        page (util/enlive-string (:body res))
        subreddit (get-in (first (en/select page [:.domain :a]))
                          [:attrs :href])
        post (:content
              (first 
               (en/select page [:#siteTable :.entry :.usertext-body :.md])))
        post-title (first
                    (:content
                     (first
                      (en/select page [:a.title]))))
        post-author (first
                     (:content
                      (first
                       (en/select page [:.top-matter :.tagline :a.author]))))
        post-text (parse-post post)]
    [:p
     [:h3 post-title]
     [:a {:href (str "https://reddit.com" subreddit)}
      subreddit]
     " ~ "
     [:font {:color "#9BABB2"}
      [:a {:href (format "https://reddit.com/user/%s/" post-author)}
       [:em post-author]]]
     [:br]
     post-text]))

(defn handle-link
  [event]
  (µ/log {:reddit event})
  (let [{:keys [channel sender host url]} event        
        post-path (.getPath url)
        post-info (get-post-info post-path)]
    (api/msg-room! channel post-info)))

(util/setup-handlers
 reddit-handler
 reddit-state
 [[url-pub {"reddit.com" [reddit-chan]
            "www.reddit.com" [reddit-chan]}]]
 reddit-chan
 handle-link)
