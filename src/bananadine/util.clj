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
  (:import [java.util Arrays])
  (:require [clojure.string :refer [join split]]
            [net.cgrand.enlive-html :as en]
            [mount.core :refer [defstate start]]
            [clojure.set :refer [intersection]]
            [clojure.core.async :refer [pub sub unsub chan >! >!! <! <!! go go-loop close! sliding-buffer]]
            [pl.danieljanus.tagsoup :as tsoup]
            [ring.util.codec :as rc]
            [com.brunobonacci.mulog :as µ])
  (:gen-class))


(def user-agent "Bananadine pre-alpha")

;; I strongly dislike some java interop restrictions
;; Avert thine eyes and forget this function exists
(defn blank-str-array
  []
  (Arrays/copyOf (into-array [""]) 0))

(defn decode-query-params
  [query-params]
  (apply hash-map (flatten (map #(split %1 #"=") (split query-params #"&")))))

(defn enlive-string
  [page]
  (with-open [s (clojure.java.io/input-stream (.getBytes page))]
    (en/html-resource s)))

(defn plain-msg
  [msg-tree]
  (join " " (filter string? (flatten msg-tree))))

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

(defmacro go-consume
  [channel handler]
  `(go-loop [msg# (<! ~channel)]
     (when msg#
       (try
         (~handler msg#)
         (catch Exception e#
           (µ/log (.getMessage e#))))
       (recur (<! ~channel)))))

(defmacro go-consume-statebound
  [state channel handler]
  `(go-loop [msg# (<! ~channel)]
     (when (and msg# (:running (deref ~state)))
       (try
         (~handler msg#)
         (catch Exception e#
           (µ/log (.getMessage e#))))
       (recur (<! ~channel)))))

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
  [state-atom handler-map channel handler]
  (let [pubbers (mapcat handler-setup-map->list handler-map)]
    (list
     (fn []
       (swap! state-atom assoc :running true)
       (doall (map (fn [[pubber topic subber]]
                     (sub pubber topic subber))
                   pubbers))
       (go-consume-statebound
        state-atom
        channel
        handler))
     (fn []
       (swap! state-atom dissoc :running)
       (doall (map (fn [[pubber topic subber]]
                     (unsub pubber topic subber))
                   pubbers))))))


(defmacro setup-handlers
  [handler-name state-atom pubsubs channel handler]
  `(let [[starter# stopper#] (handler-maps->startstops
                              ~state-atom
                              ~pubsubs
                              ~channel
                              ~handler)]
     (defstate ~handler-name
       :start (starter#)
       :stop (stopper#))))

(defn mk-chan [] (chan (sliding-buffer 8)))
