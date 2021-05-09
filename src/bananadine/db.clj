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

(ns bananadine.db
  (:import [com.mongodb MongoOptions ServerAddress])
  (:require [bananadine.util :refer [blank-str-array]]
            [clojure.set :refer [rename-keys]]
            [clojure.string :as s]
            [com.brunobonacci.mulog :as μ]
            [monger.collection :as mc]
            [monger.core :as mg]
            [monger.credentials :as mcr]
            [monger.operators :refer :all]
            [mount.core :refer [defstate]]
            [omniconf.core :as cfg])
  (:gen-class))


(declare connect-db! disconnect-db!)

(defstate dbcon
  :start (connect-db!)
  :stop (disconnect-db!))

(def act-doc "matrix_accounts")

(defn connect-db!
  []
  (let [{:keys [db-name host user pass]} (cfg/get :db)
        cred (mcr/create user db-name pass)
        dbc (mg/connect-with-credentials host cred)
        dbd (mg/get-db dbc db-name)]
    {:dbc dbc :dbd dbd}))

(defn disconnect-db!
  []
  (mg/disconnect (:dbc dbcon))
  nil)

(defn get-act
  ([host user]
   (mc/find-one-as-map (:dbd dbcon) act-doc {:host host :user user}))
  ([]
   (get-act (cfg/get :host) (cfg/get :user))))

(defn get-user-id
  ([host user]
   (let [act (get-act host user)]
     (format "@%s:%s" (:user act) (:host act))))
  ([]
   (get-user-id (cfg/get :host) (cfg/get :user))))

(defn ins-act
  [host user pass device token]
  (mc/insert (:dbd dbcon) act-doc
             {:host host
              :user user
              :pass pass
              :device device
              :txn-id 0
              :token token}))

(defn del-act
  [host user]
  (mc/remove (:dbd dbcon) act-doc {:host host :user user}))

(defn get-txn-id
  ([host user]
   (let [act (get-act host user)]
     (get act :txn-id)))
  ([]
   (get-txn-id (cfg/get :host) (cfg/get :user))))

(defn inc-txn-id!
  ([host user]
   (mc/update (:dbd dbcon)
              act-doc
              {:host host :user user}
              {$inc {:txn-id 1}}
              {:upsert true}))
  ([]
   (inc-txn-id! (cfg/get :host) (cfg/get :user))))
             
(defn get-token
  ([host user]
   (let [act (get-act host user)]
     (get act :token)))
  ([]
   (get-token (cfg/get :host) (cfg/get :user))))

(defn set-token!
  ([host user token]
   (mc/update (:dbd dbcon)
              act-doc
              {:host host :user user}
              {$set {:token token}}))
  ([token]
   (set-token! (cfg/get :host) (cfg/get :user) token)))

(defn get-in-act
  ([host user ks]
   (let [act (get-act host user)
         ks (if (seqable? ks) ks [ks])]
     (get-in act ks)))
  ([ks]
   (get-in-act (cfg/get :host) (cfg/get :user) ks)))

(defn set-in-act!
  ([host user ks v]
   (let [ks (map name (if (seqable? ks) ks [ks]))
         dotty-ks (s/join "." ks)]
     (mc/update (:dbd dbcon)
                act-doc
                {:host host :user user}
                {$set {dotty-ks v}})))
  ([ks v]
   (set-in-act! (cfg/get :host) (cfg/get :user) ks v)))

