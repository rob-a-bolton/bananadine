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

(ns bananadine.util
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go go-loop close! sliding-buffer]]
            [com.brunobonacci.mulog :as mu]
            [mount.core :as mount]
            [mount.tools.graph :refer [states-with-deps]]
            [net.cgrand.enlive-html :as en]
            [pl.danieljanus.tagsoup :as tsoup])
  (:gen-class))


(def user-agent "Bananadine pre-alpha <rob.a.bolton@gmail.com>")

(defn blank-str-array
  []
  (make-array String 0))

(defn decode-query-params
  [query-params]
  (apply hash-map (flatten (map #(str/split %1 #"=") (str/split query-params #"&")))))

(defn enlive-string
  [page]
  (with-open [s (io/input-stream (.getBytes page))]
    (en/html-resource s)))

(defn plain-msg
  [msg-tree]
  (if (string? msg-tree)
    msg-tree
    (str/join " " (filter string? (flatten msg-tree)))))

(defn extract-msg
  [event]
  (let [m (get-in event [:content :body])
        mfmt (get-in event [:content :formatted_body])]
    (merge {:plain m}
           (when mfmt
             {:html (drop 2 (nth (tsoup/parse-string mfmt) 2))}))))

(defn full-user->local-user
  [user-id]
  (second (clojure.string/split user-id #"[:@]")))

(defn go-consume-statebound
  [state channel handler]
  (go-loop [msg (<! channel)]
    (when (:running @state)
      (try
        (handler msg)
        (catch Exception e
          (mu/log (.getMessage e))))
      (recur (<! channel)))))

;; Turns [pub-chan {:topic [chan1 chan2 chan3]}]
;; into (pub-chan :topic chan1)
;;      (pub-chan :topic chan2)
;;      (pub-chan :topic chan3)
;; etc
(defn handler-setup-map->list
  [[pubber item]]
  (mapcat (fn [[k v]] (map (fn [v] (list pubber k v)) v)) item))

(defn handler-maps->pubsubs
  [handler-map]
  (let [pubsubs (mapcat handler-setup-map->list handler-map)
        sublist (map (fn [[pubber topic subber]]
                       (list sub pubber topic subber))
                     pubsubs)
        unsublist (map (fn [[pubber topic subber]]
                       (list unsub pubber topic subber))
                     pubsubs)]
    (list sublist unsublist)))

(defn handler-maps->startstops
  [state-atom handler-map chans-handlers]
  (let [pubbers (mapcat handler-setup-map->list handler-map)]
    (list
     (fn []
       (swap! state-atom assoc :running true)
       (doall (map (fn [[pubber topic subber]]
                     (sub pubber topic subber))
                   pubbers))
       (doall (map (fn [[channel handler]] (go-consume-statebound state-atom channel handler))
                   chans-handlers)))
     (fn []
       (swap! state-atom dissoc :running)
       (doall (map (fn [[pubber topic subber]]
                     (unsub pubber topic subber))
                   pubbers))))))

(defmacro setup-handlers
  [handler-name state-atom pubsubs chans-handlers]
  `(let [[starter# stopper#] (handler-maps->startstops
                              ~state-atom
                              ~pubsubs
                              ~chans-handlers)]
     (mount/defstate ~handler-name
       :start (starter#)
       :stop (stopper#))))

(defn mk-chan [] (chan (sliding-buffer 8)))

(defn map-map
  [f col]
  (into {} (map (fn [[k v]] [k (f v)]) col)))

(defn cmp-handler
  [handler]
  (comp (partial = handler) :handler))

(defn contains-hook?
  [state evt handler]
  (some (cmp-handler handler)
        (get-in @state [:hooks evt])))

(defn add-hook!
  [state evt hook]
  (when-not (contains-hook? state evt (:handler hook))
    (let [new-evt-hooks (cons hook (get-in @state [:hooks evt]))]
      (swap! state assoc-in [:hooks evt] new-evt-hooks))))

(defn rm-hook!
  [state evt handler]
  (when (contains-hook? state evt handler)
    (let [new-evt-hooks (filter (complement (cmp-handler handler))
                                (get-in @state [:hooks evt]))]
      (swap! state assoc-in [:hooks evt] new-evt-hooks))))

(defn filter-hooks
  [hooks data]
  (filter (fn [{:keys [matcher]}]
            (if matcher (matcher data) true)) hooks))

(defn run-hook!
  [handler data]
  (try
    (handler data)
    (catch Exception e (mu/log :exception
                               :err (.getMessage e)
                               :handler handler
                               :data data))))

(defn run-hooks!
  [state evt data]
  (let [hooks (filter-hooks (get-in @state [:hooks evt]) data)]
    (doall (map #(run-hook! (:handler %) data) hooks))))
