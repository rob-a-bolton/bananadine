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

(ns bananadine.matrix.api
  (:require [mount.core :refer [defstate start]]
            [clojure.string :refer [split]]
            [cheshire.core :refer :all]
            [com.brunobonacci.mulog :as µ]
            [hiccup.core :as hc]
            [bananadine.db :as db]
            [bananadine.util :refer [plain-msg full-user->local-user]]
            [clojurewerkz.ogre.core :as o]
            [clj-http.client :as client])
  (:gen-class))

(defn make-url
  "Given a stub, create a URL for the server details from db"
  [stub]
  (str "https://"
       (db/get-simple-p :server :host)
       "/"
       stub))

(defn $id
  "If ID is keyword, convert to string, else return as-is"
  [id]
  (if (keyword? id)
    (name id)
    id))

(defn get-rooms
  []
  (:body (client/get
          (make-url "/_matrix/client/r0/joined_rooms")
          {:as :json
           :oauth-token (:access_token (db/get-server-info))})))

(defn register
  "Registers a user and returns the server response"
  [host username password]
  (let [res (client/post
             (format "https://%s/_matrix/client/r0/register" host)
             {:as :json
              :body (generate-string {:auth {:type "m.login.dummy"}
                                      :username username
                                      :password password})})
        {:keys [:user_id :access_token :device_id]} (:body res)]
    {:user_id user_id
     :access_token access_token
     :device_id device_id}))

(defn register!
  "Registers a user, updates login details in db, and returns server response"
  [host username password]
  (let [res (register host username password)
        {:keys [:user_id :access_token :device_id :password]} res]    
    (o/traverse @db/g
                (o/V)
                (o/has-label :server)
                (o/property :host user_id)
                (o/property :user_id user_id)
                (o/property :access_token access_token)
                (o/property :password password)
                (o/property :device_id device_id)
                (o/iterate!))
    (db/commit-db)
    res))

(defn login
  "Logs in to server with stored credentials, returns server response"
  []
  (let [{:keys [:user_id :access_token :device_id :password]} (db/get-props-kw :server)
        res (client/post
             (make-url "_matrix/client/r0/login")
             {:as :json
              :body (generate-string
                     {:type "m.login.password"
                      :password password
                      :identifier {:type "m.id.user"
                                   :user (full-user->local-user user_id)}})})]
    (:body res)))

(defn login!
  "Logs in to server with stored credentials, updates stored access token and returns server response"
  []
  (let [res (login)
        token (:access_token res)]
    (o/traverse @db/g
                (o/V)
                (o/has-label :server)
                (o/property :access_token token)
                (o/iterate!))
    (db/commit-db)
    res))

(defn get-sync-args
  []
  (let [sv (db/get-server-info)
        timeout (or (:timeout sv) 55000)
        next-batch (:next-batch sv)]
    (when next-batch
      {:query-params {:timeout timeout
                      :since next-batch}})))

(defn sync*
  "Syncs with server, returns response"
  [& {:keys [full]}]
  (let [res
        (client/get
         (make-url "_matrix/client/r0/sync")
         (merge 
          {:as :json
           :oauth-token (:access_token (db/get-server-info))}
          (when-not full
            (get-sync-args))))]
    (:body res)))

(defn sync!
  "Syncs with server, updates next-batch, and returns response"
  [& {:keys [full]}]
  (let [body (sync* :full full)
        next-batch (:next_batch body)]
    (when next-batch
      (db/set-simple-p! :server :next-batch next-batch))
    body))

(defn join-room
  [room-id]
  (:body (client/post
          (make-url (str "_matrix/client/r0/rooms/" room-id "/join"))
          {:as :json
           :oauth-token (:access_token (db/get-server-info))})))

;; Clients should limit the HTML they render to avoid Cross-Site Scripting, HTML injection, and similar attacks. The strongly suggested set of HTML tags to permit, denying the use and rendering of anything else, is: font, del, h1, h2, h3, h4, h5, h6, blockquote, p, a, ul, ol, sup, sub, li, b, i, u, strong, em, strike, code, hr, br, div, table, thead, tbody, tr, th, td, caption, pre, span, img.

;; Not all attributes on those tags should be permitted as they may be avenues for other disruption attempts, such as adding onclick handlers or excessively large text. Clients should only permit the attributes listed for the tags below. Where data-mx-bg-color and data-mx-color are listed, clients should translate the value (a 6-character hex color code) to the appropriate CSS/attributes for the tag.
;; font:	data-mx-bg-color, data-mx-color
;; span:	data-mx-bg-color, data-mx-color
;; a:	name, target, href (provided the value is not relative and has a scheme matching one of: https, http, ftp, mailto, magnet)
;; img:	width, height, alt, title, src (provided it is a Matrix Content (MXC) URI)
;; ol:	start
;; code:	class (only classes which start with language- for syntax highlighting)

(defn msg-room!
  [room-id msg]
  (µ/log {:room-id room-id :msg msg})
  (let [res (client/put
             (make-url (str "/_matrix/client/r0/rooms/"
                            ($id room-id)
                            "/send/m.room.message/"
                            (db/get-txn-id)))
             {:as :json
              :oauth-token (:access_token (db/get-server-info))
              :body (generate-string
                     {:msgtype "m.text"
                      :format "org.matrix.custom.html"
                      :formatted_body (hc/html msg)
                      :body (plain-msg msg)})})]
    (:body res)))
