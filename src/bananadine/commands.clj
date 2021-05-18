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


(ns bananadine.commands
  (:require [bananadine.db :as db]
            [bananadine.matrix.events :refer [event-state]]
            [bananadine.util :as util]
            [clojure.string :as str]
            [mount.core :refer [defstate]]))

(def command-atom (atom {}))

(defn cmd-str
  []
  (or (db/get-in-act :cmd-str)
      "~"))

(defn is-cmd?
  [s]
  (and (> (.length s) (.length (cmd-str)))
       (str/starts-with? s (cmd-str))))

(defn parse-as-cmd
  [s]
  (when (is-cmd? s)
    (let [parts (str/split (.substring s (.length (cmd-str))) #" +")
          argstr (.substring s (+ (.length (cmd-str))
                                  (.length (first parts))))]
      {:cmd (keyword (first parts))
       :args (rest parts)
       :comma-args (map str/trim (str/split argstr #"[,.;]"))
       :argstr argstr})))

(defn extract-cmd
  [event]
  (when-let [cmd (parse-as-cmd (get-in event [:msg :plain]))]
    (util/run-hooks! command-atom
                     (:cmd cmd)
                     (merge event
                            cmd))))

(defn start-command-state!
  []
  (util/add-hook! event-state
                  :room-msg
                  {:handler extract-cmd})
  command-atom)

(defn stop-command-state!
  []
  (util/rm-hook! event-state :room-msg extract-cmd)
  (reset! command-atom {}))

(defstate command-state
  :start (start-command-state!)
  :stop (stop-command-state!))
