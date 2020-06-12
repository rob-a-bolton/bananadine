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

(ns bananadine.matrix.auth
  (:require [bananadine.db :as db]
            [bananadine.matrix.connection :refer [conn make-url]]
            [cheshire.core :refer :all]
            [clj-http.client :as client]
            [clojurewerkz.ogre.core :as o]
            [com.brunobonacci.mulog :as µ]
            [mount.core :refer [defstate]])
  (:gen-class))


(defn register
  [username password]
  (client/post 
   (make-url "_matrix/client/r0/register")
   {:async? true
    :as :json
    :body (generate-string {:auth {:type "m.login.dummy"}
                            :username username
                            :password password
                            :initial_device_display_name "bot"})}
   ; Success
   (fn [response]
;     (µ/log response)
     (o/traverse @db/g (o/V)
                 (o/has-label :server)
                 (o/property :username username)
                 (o/property :password password)
                 (o/property :user_id (:user_id (:body response)))
                 (o/property :device_id (:device_id (:body response)))
                 (o/property :token (:access_token (:body response)))
                 (o/next!))
     (db/commit-db)
     (swap! conn assoc :username username))
   ; Failure
   (fn [response]
     (µ/log response))))

(defn get-account-details
  []
  (let [server-vertex (first (o/traverse @db/g (o/V)
                                         (o/has-label :server)
                                         (o/into-list!)))]
    (when server-vertex
      {:username (db/get-prop server-vertex "username")
       :password (db/get-prop server-vertex "password")
       :user_id (db/get-prop server-vertex "user_id")
       :device_id (db/get-prop server-vertex "device_id")
       :token (db/get-prop server-vertex "token")})))

(defn set-token
  [token]
  (swap! conn assoc :token token)
  (db/set-simple-p! :server "token" token))

(defn get-login-params
  []
  (let [{:keys [:username :password :user_id :device_id :token]}
               (get-account-details)]
    (merge {:type "m.login.password"
            :identifier {:type "m.id.user"
                         :user user_id}
            :password password}
           (if token {:session token}))))

(defn login
  []
  (let [{:keys [:username :password :user_id :device_id :token]}
               (get-account-details)]
    (client/post
     (make-url "_matrix/client/r0/login")
     {:async? true
      :as :json
      :body (generate-string (get-login-params))}
     ; Success
     (fn [response]
       (set-token (get-in response [:body :access_token])))
     ; Failure
     (fn [response]
       (µ/log response)))))
