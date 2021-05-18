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

(ns bananadine.commands.random
  (:require [bananadine.matrix.api :as api]
            [bananadine.db :as db]
            [bananadine.commands :refer [command-state]]
            [bananadine.util :as util]
            [clj-http.client :as client]
            [clojure.string :as str]
            [mount.core :refer [defstate]]
            [net.cgrand.enlive-html :as en]))


(def random-atom (atom {}))

(defn choose-handler
  "Given a selection of items as choices, pick a random item"
  [event]
  (when-let [choice (rand-nth (:comma-args event))]
    (api/msg-room! (:channel event) choice)))

(def letter-frequencies
  {"a" 11.602
   "b" 4.702
   "c" 3.511
   "d" 2.67
   "e" 2.007
   "f" 3.779
   "g" 1.95
   "h" 7.232
   "i" 6.286
   "j" 0.597
   "k" 0.59
   "l" 2.705
   "m" 4.374
   "n" 2.365
   "o" 6.264
   "p" 2.545
   "q" 0.173
   "r" 1.653
   "s" 7.755
   "t" 16.671
   "u" 1.487
   "v" 0.649
   "w" 6.753
   "x" 0.037
   "y" 1.62
   "z" 0.034})

;; https://stackoverflow.com/questions/14464011/idiomatic-clojure-for-picking-between-random-weighted-choices
;; Stuart Halloway's `data.generators` approach
(defn choose-weighted
  "Given a k/v map of item/weight, choose a random item"
  [items]
  (let [weights (reductions + (vals items))
        total (last weights)
        choices (map vector (keys items) weights)
        choice (rand total)]
    (loop [[[c w] & more] choices]
      (if (< choice w)
        c
        (recur more)))))

(defn acronym-handler
  "Respond to an acronym command with a random acronym"
  [event]
  (when-let [num-letters (Integer/parseInt (first (:args event)))]
    (let [letters (repeatedly num-letters #(choose-weighted letter-frequencies))
          acronym (str/join letters)]
      (api/msg-room! (:channel event) acronym))))

(defn start-random-state!
  []
  (util/add-hook! command-state
                  :acronym
                  {:handler acronym-handler})
  (util/add-hook! command-state
                  :choose
                  {:handler choose-handler})
  random-atom)

(defn stop-random-state!
  []
  (util/rm-hook! command-state :acronym acronym-handler)
  (util/rm-hook! command-state :choose choose-handler)
  (reset! random-atom {}))

(defstate random-state
  :start (start-random-state!)
  :stop (stop-random-state!))
