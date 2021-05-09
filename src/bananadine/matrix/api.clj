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
  (:require [bananadine.db :as db]
            [bananadine.util :refer [plain-msg]]
            [cheshire.core :refer [generate-string]]
            [clj-http.client :as client]
            [com.brunobonacci.mulog :as µ]
            [hiccup.core :as hc])
  (:gen-class))

(defn make-url
  "Given a stub, create a URL for the server details from db"
  [stub]
  (str "https://"
       (db/get-in-act :host)
       "/"
       stub))

(defn get-rooms
  []
  (:body (client/get
          (make-url "_matrix/client/r0/joined_rooms")
          {:as :json
           :oauth-token (db/get-token)})))

(defn register
  "Registers a user and returns the server response"
  [host user pass]
  (let [res (client/post
             (format "https://%s/_matrix/client/r0/register" host)
             {:as :json
              :body (generate-string {:auth {:type "m.login.dummy"}
                                      :username user
                                      :password pass})})
        {:keys [:user_id :access_token :device_id]} (:body res)]
    {:user-id user_id
     :access-token access_token
     :device-id device_id}))

(defn register!
  "Registers a user, updates login details in db, and returns server response"
  [host user pass]
  (let [res (register host user pass)
        {:keys [access-token device-id]} res]
    (db/ins-act host user pass device-id access-token)
    res))

(defn login
  "Logs in to server with stored credentials, returns server response"
  []
  (let [{:keys [:user :pass]} (db/get-act)
        res (client/post
             (make-url "_matrix/client/r0/login")
             {:as :json
              :body (generate-string
                     {:type "m.login.password"
                      :password pass
                      :identifier {:type "m.id.user"
                                   :user user}})})]
    (:body res)))

(defn login!
  "Logs in to server with stored credentials, updates stored access token and returns server response"
  []
  (let [res (login)
        token (:access_token res)]
    (db/set-token! token)
    res))

(defn get-sync-args
  []
  (let [act (db/get-act)
        timeout (or (:timeout act) 55000)
        next-batch (:next-batch act)]
    (when next-batch
      {:query-params {:timeout timeout
                      :since next-batch}})))

(defn join-room
  [room-id]
  (:body (client/post
          (make-url (str "_matrix/client/r0/rooms/" room-id "/join"))
          {:as :json
           :oauth-token (db/get-token)})))

(defn sync*
  "Syncs with server, returns response"
  [& {:keys [full]}]
  (let [res
        (client/get
         (make-url "_matrix/client/r0/sync")
         (merge 
          {:as :json
           :oauth-token (db/get-token)}
          (when-not full
            (get-sync-args))))]
    (:body res)))

(defn sync!
  "Syncs with server, updates next-batch, and returns response. Joins any invites"
  [& {:keys [full]}]
  (let [body (sync* :full full)
        next-batch (:next_batch body)
        invites (keys (get-in body [:rooms :invite]))]
    (when next-batch
      (db/set-in-act! :next-batch next-batch))
    (doseq [room-id invites]
      (join-room (name room-id)))
    body))

;; Clients should limit the HTML they render to avoid Cross-Site Scripting, HTML injection, and similar attacks. The strongly suggested set of HTML tags to permit, denying the use and rendering of anything else, is: font, del, h1, h2, h3, h4, h5, h6, blockquote, p, a, ul, ol, sup, sub, li, b, i, u, strong, em, strike, code, hr, br, div, table, thead, tbody, tr, th, td, caption, pre, span, img.

;; Not all attributes on those tags should be permitted as they may be avenues for other disruption attempts, such as adding onclick handlers or excessively large text. Clients should only permit the attributes listed for the tags below. Where data-mx-bg-color and data-mx-color are listed, clients should translate the value (a 6-character hex color code) to the appropriate CSS/attributes for the tag.
;; font:	data-mx-bg-color, data-mx-color
;; span:	data-mx-bg-color, data-mx-color
;; a:	name, target, href (provided the value is not relative and has a scheme matching one of: https, http, ftp, mailto, magnet)
;; img:	width, height, alt, title, src (provided it is a Matrix Content (MXC) URI)
;; ol:	start
;; code:	class (only classes which start with language- for syntax highlighting)

(defn msg-room!
  [room-id msg & {:keys [notice] :or {notice true}}]
  (µ/log {:room-id room-id :msg msg})
  (let [res (client/put
             (make-url (str "/_matrix/client/r0/rooms/"
                            (name room-id)
                            "/send/m.room.message/"
                            (db/get-txn-id)))
             {:as :json
              :oauth-token (db/get-token)
              :body (generate-string
                     {:msgtype (if notice "m.notice" "m.text")
                      :format "org.matrix.custom.html"
                      :formatted_body (hc/html msg)
                      :body (plain-msg msg)})})]
    (db/inc-txn-id!)
    (:body res)))
