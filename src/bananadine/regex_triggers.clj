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

(ns bananadine.regex-triggers
  (:require [bananadine.commands :refer [command-state register-cmd! unregister-cmd!]]
            [bananadine.db :as db]
            [bananadine.util :as util]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.connection :refer [conn]]
            [bananadine.matrix.events :refer [event-state]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [com.brunobonacci.mulog :as mu]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [mount.core :refer [defstate]]
            [clojure.string :as str])
  (:gen-class))


(def regex-trigger-atom (atom {}))

(def regex-trigger-collection "regex_triggers")

(defn db-upsert-regex-trigger
  [name obj]
  (mc/update (:dbd db/dbcon)
             regex-trigger-collection
             {:name name}
             (assoc obj :name name)
             {:upsert true}))

(defn db-rm-regex-trigger
  [name]
  (mc/remove (:dbd db/dbcon)
             regex-trigger-collection
             {:name name}))

(defn get-regexes-from-db
  []
  (->> (mc/find-maps (:dbd db/dbcon) regex-trigger-collection)
       (map (fn [r] [(:name r) (dissoc r :name)]))
       (into {})))

(defn upsert-regex-trigger
  [name obj]
  (let [current-obj (get-in @regex-trigger-atom
                            [:regexes name]
                            obj)
        new-obj (merge current-obj obj)]
    (swap! regex-trigger-atom assoc-in [:regexes name] new-obj)
    (db-upsert-regex-trigger name new-obj)))

(defn set-regex-trigger
  [name regex]
  (let [current-obj (get-in @regex-trigger-atom
                            [:regexes name])
        new-obj (assoc current-obj :regex regex)]
    (swap! regex-trigger-atom assoc-in [:regexes name] new-obj)
    (db-upsert-regex-trigger name new-obj)))

(defn rm-regex-trigger
  [name]
  (let [new-regexes (dissoc (:regexes @regex-trigger-atom) name)]
    (swap! regex-trigger-atom assoc :regexes new-regexes)
    (db-rm-regex-trigger name)))

(defn set-regex-trigger-prob
  [name prob]
  (swap! regex-trigger-atom assoc-in [:regexes name :prob] prob)
  (db-upsert-regex-trigger name (get-in @regex-trigger-atom [:regexes name])))

(defn add-regex-trigger-resp
  [name resp]
  (let [current-obj (get-in @regex-trigger-atom [:regexes name])
        new-obj (assoc current-obj :resps (cons resp (:resps current-obj)))]
    (swap! regex-trigger-atom assoc-in [:regexes name] new-obj)
    (db-upsert-regex-trigger name new-obj)))

(defn rm-regex-trigger-resp
  [name i]
  (let [current-obj (get-in @regex-trigger-atom [:regexes name])
        resps (:resps current-obj)
        new-resps (concat (take i resps)
                          (drop (inc i) resps))
        new-obj (assoc current-obj :resps new-resps)]
    (swap! regex-trigger-atom assoc-in [:regexes name] new-obj)
    (db-upsert-regex-trigger name new-obj)))

(defn regex-matches
  [s]
  (filter (fn [[_ v]] (re-matches (re-pattern (:regex v)) s))
          (:regexes @regex-trigger-atom)))

(def rx-resp-groups #"\{([^}]+)\}")

(defn mk-resp-replacer
  [mget]
  (fn [[_ s]]
    (mget s)))

(defn mk-mget
  [m]
  (re-find m)
  (fn [k]
    (try
      (.group m (if-let [i (util/maybe-int k)] i k))
      (catch IllegalArgumentException _ nil))))

(defn run-regex
  [regex s resps prob]
  (let [m (re-matcher regex s)
        mget (mk-mget m)
        resp-replacer (mk-resp-replacer mget)
        resp (rand-nth resps)]
    (when (and resp (>= (rand) (- 1 (or prob 1.0))))
      (str/replace resp rx-resp-groups resp-replacer))))

(defn handle-regex-triggers
  [{:keys [msg channel]}]
  (let [msg (:plain msg)
        matches (regex-matches msg)
        responses (->> matches
                       (map (fn [[_ v]] (run-regex (re-pattern (:regex v))
                                                          msg
                                                          (:resps v)
                                                          (:prob v))))
                       (filter some?))]
    (when (seq responses)
      (api/msg-room! channel (str/join "\n" responses)))))

(defn warn-regex-no-exist
  [channel rx-name]
  (api/msg-room! channel [:p "Regex " [:strong rx-name] " does not exist"]))

(defn print-help
  [channel]
  (api/msg-room! channel
   [:p
    [:strong "regex create "] [:em "name regex"] ": Create a named regex" [:br]
    [:strong "regex list"] ": Lists the named regexes" [:br]
    [:strong "regex set "] [:em "name regex"] ": Change a named regex" [:br]
    [:strong "regex rm! "] [:em "name"] ": Delete a named regex" [:br]
    [:strong "regex resp "] [:em "name"] ": Show the responses for a regex" [:br]
    [:strong "regex resp add "] [:em "name response"] ": Add a response to a regex" [:br]
    [:strong "regex resp rm "] [:em "name index"] ": Delete a response from a regex" [:br]
    [:strong "regex chance "] [:em "name"] ": Show the chance of triggering a regex" [:br]
    [:strong "regex chance "] [:em "name chance"] ": Set the chance of triggering a regex" [:br]]))

(defn handle-create-cmd
  [channel rx-name regex]
  (upsert-regex-trigger rx-name {:regex regex})
  (api/msg-room! channel (format "Created regex %s" rx-name)))

(defn handle-list-cmd
  [channel]
  (let [objs (:regexes @regex-trigger-atom)]
    (if (seq objs)
      (api/msg-room! channel
                     [:p [:h6 "Regexes"] [:br]
                      (->> objs
                           (map (fn [[rx-name obj]] [[:strong rx-name] " " (:regex obj)]))
                           (interpose [[:br]])
                           (apply concat))])
      (api/msg-room! channel "No regexes created yet"))))

(defn handle-set-cmd
  [channel rx-name regex]
  (let [obj (get-in @regex-trigger-atom [:regexes rx-name])]
    (if obj
      (do (set-regex-trigger rx-name regex)
          (api/msg-room! channel [:p "Updated regex " [:strong rx-name] " to " regex]))
      (warn-regex-no-exist channel rx-name))))

(defn handle-rm-cmd
  [channel rx-name]
  (rm-regex-trigger rx-name)
  (api/msg-room! channel (format "Deleted regex %s" rx-name)))

(defn indexed-responses
  [responses]
  (apply concat (interpose [[:br]] (map-indexed (fn [i r] [[:strong (str i ": ")] r]) responses))))

(defn handle-resp-print-cmd
  [channel rx-name]
  (let [obj (get-in @regex-trigger-atom
                    [:regexes rx-name])
        regex (:regex obj)
        responses (:resps obj)]
    (cond
      (nil? obj)
        (warn-regex-no-exist channel rx-name)
      ((complement seq) responses)
        (api/msg-room! channel [:p "Regex " [:strong rx-name] " has no responses"])
      :else
        (api/msg-room! channel
                       [:p [:h5 rx-name]
                           [:h6 regex]
                           (indexed-responses responses)]))))

(defn handle-resp-add-cmd
  [channel rx-name resp]
  (if (get-in @regex-trigger-atom [:regexes rx-name])
    (do (add-regex-trigger-resp rx-name resp)
        (api/msg-room! channel [:p "Added response " [:em resp] " to regex " [:strong rx-name]]))
    (warn-regex-no-exist channel rx-name)))

(defn handle-resp-rm-cmd
  [channel rx-name index]
  (let [obj (get-in @regex-trigger-atom [:regexes rx-name])
        resps (:resps obj)
        resp-i (nth resps index nil)
        num-resps (count resps)]
    (cond
      (nil? obj)
        (warn-regex-no-exist channel rx-name)
      (<= num-resps index)
        (api/msg-room! channel [:p "Regex " [:strong rx-name] " only has " (str num-resps) " responses"])
      :else
        (do (rm-regex-trigger-resp rx-name index)
            (api/msg-room! channel [:p "Removed " [:em resp-i] " from " [:strong rx-name]])))))

(defn handle-resp-show-chance-cmd
  [channel rx-name]
  (let [obj (get-in @regex-trigger-atom [:regexes rx-name])
        chance (or (:prob obj) 1.0)]
    (api/msg-room! channel [:p "Chance to trigger " [:strong rx-name] ": " (str chance)])))

(defn handle-resp-set-chance-cmd
  [channel rx-name chance]
  (let [obj (get-in @regex-trigger-atom [:regexes rx-name])
        current-chance (or (:prob obj) 1.0)]
    (if obj
      (do (set-regex-trigger-prob rx-name chance)
          (api/msg-room! channel [:p "Changed chance for regex "
                                  [:strong rx-name]
                                  " from "
                                  (str current-chance)
                                  " to "
                                  (str chance)]))
      (warn-regex-no-exist channel rx-name))))

(def regex-cmd-def
  {:name "regex"
   :desc "Manage named regex triggers and responses"
   :cmds [{:cmd "create"
           :desc "Create a named regex. Name a capture group via (?<foo>)"
           :handler handle-create-cmd
           :args [{:name :name} {:name :regex}]}
          {:cmd "set"
           :desc "Change a named regex"
           :handler handle-set-cmd
           :args [{:name :name} {:name :regex}]}
          {:cmd "rm!"
           :desc "Delete a named regex"
           :handler handle-rm-cmd
           :args [{:name :name}]}
          {:cmd "list"
           :desc "List the named regexes"
           :handler handle-list-cmd
           :args []}
          {:cmd "resp"
           :desc "Show the responses for a regex"
           :handler handle-resp-print-cmd
           :args [{:name :name}]}
          {:cmd "resp add"
           :desc "Add a response to a regex. {1}, {2}, {foo} etc for capture groups"
           :handler handle-resp-add-cmd
           :args [{:name :name} {:name :resp}]}
          {:cmd "resp rm"
           :desc "Delete a response from a regex by index"
           :handler handle-resp-rm-cmd
           :args [{:name :name}]}
          {:cmd "chance"
           :desc "Show the chance of triggering a regex"
           :handler handle-resp-show-chance-cmd
           :args [{:name :name}]}
          {:cmd "chance set"
           :desc "Set the chance of triggering a regex"
           :handler handle-resp-set-chance-cmd
           :args [{:name :name} {:name :chance :converter #(Float/parseFloat %)}]}]})

(defn start-regex-trigger-state!
  []
  (reset! regex-trigger-atom {:regexes (get-regexes-from-db)})
  (util/add-hook! event-state :m.text {:handler handle-regex-triggers})
  (register-cmd! regex-cmd-def)
  regex-trigger-atom)

(defn stop-regex-trigger-state!
  []
  (util/rm-hook! event-state :m.text handle-regex-triggers)
  (unregister-cmd! regex-cmd-def)
  (reset! regex-trigger-atom {}))

(defstate regex-trigger-state
  :start (start-regex-trigger-state!)
  :stop (stop-regex-trigger-state!))
