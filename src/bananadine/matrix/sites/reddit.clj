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
  (:require [mount.core :refer [defstate]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-state host-matcher]]
            [bananadine.util :as util]
            [clj-http.client :as client]
            [net.cgrand.enlive-html :as en]
            [com.brunobonacci.mulog :as mu])
  (:gen-class))

(def reddit-atom (atom {}))

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
  (mu/log {:reddit event})
  (let [{:keys [channel url]} event
        post-path (.getPath url)
        post-info (get-post-info post-path)]
    (api/msg-room! channel post-info)))

(defn start-reddit-state!
  []
  (util/add-hook! url-state
                  :url
                  {:handler handle-link
                   :matcher (host-matcher ["reddit.com"])}))

(defn stop-reddit-state!
  []
  (util/rm-hook! url-state :url handle-link)
  (reset! reddit-atom {}))

(defstate reddit-state
  :start (start-reddit-state!)
  :stop (stop-reddit-state!))
