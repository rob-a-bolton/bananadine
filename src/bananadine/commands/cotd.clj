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


(ns bananadine.commands.cotd
  (:require [bananadine.matrix.api :as api]
            [bananadine.db :as db]
            [bananadine.commands :refer [command-state register-cmd! unregister-cmd!]]
            [bananadine.util :as util]
            [clj-http.client :as client]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [net.cgrand.enlive-html :as en]))

(def cotd-atom (atom {}))

(defn get-cotd
  []
  (let [res (client/get "https://cheese.com"
                        {:headers {"User-Agent" util/user-agent}})
        page (util/enlive-string (:body res))
        cotd-a (first (en/select page [:#cheese-of-day :a]))
        cotd-stub (get-in cotd-a [:attrs :href])
        res (client/get (str "https://cheese.com" cotd-stub))
        page (util/enlive-string (:body res))
        details-box (en/select page [:.catalog :.detail :.unit])
        cheese-name (-> (en/select details-box [:h1])
                        first
                        :content
                        first
                        str/trim)
        cheese-desc (-> (en/select details-box [:.summary :.description])
                        first
                        en/text
                        str/trim)]
    {:name cheese-name
     :desc cheese-desc}))

(defn handle-cotd
  [channel]
  (let [{:keys [name desc]} (get-cotd)]
    (api/msg-room! channel
      [:p [:h1 name] [:p desc]])))

(def cotd-cmd-def
  {:name "cotd"
   :desc "Cheese of the day module"
   :cmds [{:cmd "" :args [] :desc "Gets the cheese of the day" :handler handle-cotd}]})

(defn start-cotd-state!
  []
  (register-cmd! cotd-cmd-def)
  cotd-atom)

(defn stop-cotd-state!
  []
  (unregister-cmd! cotd-cmd-def)
  (reset! cotd-atom {}))

(defstate cotd-state
  :start (start-cotd-state!)
  :stop (stop-cotd-state!))
