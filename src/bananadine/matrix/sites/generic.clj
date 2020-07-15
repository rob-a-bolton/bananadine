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
  (:require [mount.core :refer [defstate start]]
            [net.cgrand.enlive-html :as en]
            [bananadine.db :as db]
            [bananadine.util :as util]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.urls :refer [urls-pub]]
            [clojure.java.io :refer [as-url]]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go]]
            [com.brunobonacci.mulog :as µ]
            [bananadine.matrix.events :refer [event-handler event-pub]])
  (:gen-class))

(def url-state (atom {}))
(def link-chan (util/mk-chan))

(defn handle-link
  [event]
  (when-not (get (db/get-props-kw :nourl-chans) (keyword (:channel event)))
    (let [{:keys [channel sender host url]} event
          page (en/html-resource url)
          title (clojure.string/join 
                 (flatten 
                  (:content (first (en/select page [:title])))))]
      (api/msg-room! channel
        [:p [:a {:href url} title]]))))

(util/setup-handlers
 generic-handler
 url-state
 [[urls-pub {true [link-chan]}]]
 link-chan
 handle-link)
