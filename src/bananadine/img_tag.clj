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


(ns bananadine.img-tag
  (:require [bananadine.commands :refer [command-state
                                         register-cmd!
                                         unregister-cmd!
                                         register-fallback-handler!
                                         unregister-fallback-handler!]]
            [bananadine.db :refer [dbcon]]
            [bananadine.matrix.api :as api]
            [bananadine.matrix.events :refer [event-collection]]
            [bananadine.util :as util]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [monger.query :as q]
            [mount.core :refer [defstate]]
            [clojure.string :as str]))


(def img-tag-atom (atom {}))

(def img-tag-collection "img_tags")

(defn warn-invalid-img
  [channel img-name]
  (api/msg-room! channel (list  "Image " [:strong img-name] " does not exist")))

(defn get-last-img-info
  []
  (-> (q/with-collection (:dbd dbcon) event-collection
        (q/find {:content.msgtype "m.image"})
        (q/fields [:content :origin_server_ts])
        (q/sort (array-map :origin_server_ts -1))
        (q/limit 1))
      first
      :content
      util/extract-img-msg))

(defn get-named-imgs
  []
  (mc/find-maps (:dbd dbcon) img-tag-collection))

(defn get-named-img
  [img-name]
  (mc/find-one-as-map (:dbd dbcon) img-tag-collection
                      {:name img-name}))

(defn rm-named-img
  [img-name]
  (= 1 (.getN (mc/remove (:dbd dbcon) img-tag-collection
                         {:name img-name}))))

(defn rename-img
  [img-name new-name]
  (= 1 (.getN (mc/update (:dbd dbcon) img-tag-collection
                         {:name img-name}
                         {$set {:name new-name}}))))

(defn name-last-img
  [img-name]
  (let [img (get-last-img-info)]
    (mc/insert (:dbd dbcon) img-tag-collection
               {:name img-name
                :uri (get-in img [:url :uri])
                :img-info (:img-info img)})))

(defn tag-named-img
  [img-name tag]
  (= 1 (.getN (mc/update (:dbd dbcon) img-tag-collection
                         {:name img-name}
                         {$addToSet {:tags tag}}))))

(defn untag-named-img
  [img-name tag]
  (= 1 (.getN (mc/update (:dbd dbcon) img-tag-collection
                         {:name img-name}
                         {$pull {:tags tag}}))))

(defn get-tag-imgs
  [tag]
  (mc/find-maps (:dbd dbcon) img-tag-collection
                {:tags {$in [tag]}}))

(defn get-tags
  []
  (->> (mc/find-maps (:dbd dbcon) img-tag-collection
                     {:tags {$exists true $ne []}}
                     {:tags true})
       (map :tags)
       (apply concat)
       set))

(defn printable-img-info
  [img]
  (let [img-name (:name img)
        tags (:tags img)]
    (concat [[:strong img-name]]
            (when (seq tags)
              [[:em " tags: "] (str/join ", " tags)]))))

(defn handle-name-img
  [channel img-name]
  (name-last-img img-name)
  (api/msg-room! channel (list "Named last image " [:strong img-name])))

(defn handle-rm-img
  [channel img-name]
  (if (rm-named-img img-name)
    (api/msg-room! channel (list "Deleted image named " [:strong img-name]))
    (warn-invalid-img channel img-name)))

(defn handle-list-imgs
  [channel]
  (let [imgs (get-named-imgs)]
    (if (seq imgs)
      (api/msg-room! channel (->> (map printable-img-info imgs)
                                  (interpose [[:br]])
                                  (apply concat)))
      (api/msg-room! channel "No images have been named"))))

(defn handle-rename-img
  [channel img-name new-name]
  (if (rename-img img-name new-name)
    (api/msg-room! channel
       (list  "Renamed image "
              [:strong img-name]
              " to "
              [:strong new-name]))
    (warn-invalid-img channel img-name)))

(defn handle-tag-img
  [channel img-name tag]
  (if (tag-named-img img-name tag)
    (api/msg-room! channel
       (list "Tagged image "
             [:strong img-name]
             " with tag "
             [:em tag]))
    (warn-invalid-img channel img-name)))

(defn handle-untag-img
  [channel img-name tag]
  (if (untag-named-img img-name tag)
    (api/msg-room! channel
       [(list "Removed tag "
              [:em tag]
              " from image "
              [:strong img-name])])
    (warn-invalid-img channel img-name)))

(defn handle-list-tags
  [channel]
  (if-let [tags (sort (get-tags))]
    (api/msg-room! channel (str/join ", " tags))
    (api/msg-room! channel "No images have been tagged yet")))

(defn handle-find-tagged
  [channel tag]
  (if-let [imgs (seq (get-tag-imgs tag))]
    (api/msg-room! channel (->> (map printable-img-info imgs)
                                (interpose [[:br]])
                                (apply concat)))
    (api/msg-room! channel (list "Tag " [:strong tag] " does not exist"))))

(defn handle-img-info
  [channel img-name]
  (if-let [img (get-named-img img-name)]
    (api/msg-room! channel (seq (printable-img-info img)))
    (warn-invalid-img channel img-name)))

(defn fallback-img-handler
  [channel img-name _]
  (let [img (get-named-img img-name)
        tagged (seq (get-tag-imgs img-name))]
    (cond
      img (api/post-img! channel (:uri img) (:img-info img) (:name img))
      tagged (let [img (rand-nth tagged)] (api/post-img! channel (:uri img) (:img-info img) (:name img))))))

(def img-cmd-def
  {:name "img"
   :desc "Manage named and tagged images"
   :cmds [{:cmd "name"
           :desc "Named the last image posted"
           :handler handle-name-img
           :args [{:name :name}]}
          {:cmd "rename"
           :desc "Rename an image"
           :handler handle-rename-img
           :args [{:name :name} {:name :new-name}]}
          {:cmd "rm!"
           :desc "Delete a named image"
           :handler handle-rm-img
           :args [{:name :name}]}
          {:cmd "list"
           :desc "List the named regexes"
           :handler handle-list-imgs
           :args []}
          {:cmd "info"
           :desc "Show info about a named image (e.g tags)"
           :handler handle-img-info
           :args [{:name :name}]}
          {:cmd "tag add"
           :desc "Add a tag to an image"
           :handler handle-tag-img
           :args [{:name :name} {:name :tag}]}
          {:cmd "tag rm"
           :desc "Remove a tag from an image"
           :handler handle-untag-img
           :args [{:name :name} {:name :tag}]}
          {:cmd "tag list"
           :desc "Lists tags used"
           :handler handle-list-tags
           :args []}
          {:cmd "tag find"
           :desc "Finds all images associated with a tag"
           :handler handle-find-tagged
           :args [{:name :tag}]}]})

(defn start-img-tag-state!
  []
  (register-cmd! img-cmd-def)
  (register-fallback-handler! fallback-img-handler)
  img-tag-atom)

(defn stop-img-tag-state!
  []
  (unregister-cmd! img-cmd-def)
  (unregister-fallback-handler! fallback-img-handler)
  (reset! img-tag-atom {}))

(defstate img-tag-state
  :start (start-img-tag-state!)
  :stop (stop-img-tag-state!))
