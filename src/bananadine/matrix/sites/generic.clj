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


(ns bananadine.matrix.sites.generic
  (:require [bananadine.db :as db]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [url-state]]
            [bananadine.matrix.rooms :as rooms]
            [bananadine.util :as util]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [clojure.java.io :refer [as-url]]
            [clojure.string :as str]
            [com.brunobonacci.mulog :as mu]
            [mount.core :refer [defstate start]]
            [net.cgrand.enlive-html :as en])
  (:gen-class))

(def generic-atom (atom {}))

(defn meta-tag-content
  [proptype tag page]
  (get-in (first (en/select page [(en/attr= proptype tag)]))
          [:attrs :content]))

(defn get-tag-title
  [page]
  (str/join " " (flatten (:content (first (en/select page [:title]))))))

(defn get-title
  [page]
  (or (meta-tag-content :property "og:title" page)
      (meta-tag-content :name "title" page)
      (meta-tag-content :name "twitter:title" page)
      (get-tag-title page)))

(defn get-description
  [page]
  (or (meta-tag-content :property "og:description" page)
      (meta-tag-content :name "description" page)
      (meta-tag-content :name "twitter:description" page)))

(defn handle-link
  [channel sender host url]
  (mu/log {:generic url})
  (let [page (en/html-resource url)
        title (get-title page)
        desc (get-description page)
        desc (and (not= title desc) desc)]
    (when title
      (api/msg-room! channel
                     [:p [:a {:href url} title]
                      (when desc [:p [:em desc]])]))))

(defn handle-links
  [event]
  (let [{:keys [channel sender host url]} event
        muted? (rooms/get-room channel :no-generic)]
    (when-not muted?
      (handle-link channel sender host url))))

(defn start-generic-state!
  []
  (util/add-hook! url-state
                  :url
                  {:handler handle-links}))
(defn stop-generic-state!
  []
  (util/rm-hook! url-state :url handle-links)
  (reset! generic-atom {}))

(defstate generic-state
  :start (start-generic-state!)
  :stop (stop-generic-state!))
