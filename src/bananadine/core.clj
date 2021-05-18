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


(ns bananadine.core
  (:require [bananadine.bootstrap :refer [default-handlers]]
            [bananadine.db :as db]
            [bananadine.logger]
            [bananadine.matrix.api :as api]
            [clojure.tools.cli :refer [parse-opts]]
            [com.brunobonacci.mulog :as mu]
            [mount.core :refer [defstate start]]
            [omniconf.core :as cfg])
  (:gen-class))

;; "Custom" pretty printing logs
;; i.e. baby's first custom logger for µ, straight from
;; the example documentation


(def cli-options
  [["-r" "--register" "Register the bot"]
   ["-d" "--host DOMAIN" "Specifies host domain"]
   ["-u" "--username USER" "Specifies username"]
   ["-p" "--password PASS" "Specifies password"]
   [nil "--update" "Updates password"]
   ;; ["-l" "--logfile FILE" "Specifies log file location"
   ;;  :default "/tmp/bananadine.edn"]
   ["-c" "--config FILE" "Specifies config file"]
   ["-h" "--help"]])

(defn print-and-exit
  [msg & {:keys [error] :or {error false}}]
  (binding [*out* *err*]
    (println msg)
    (System/exit (if :error 1 0))))

(defn error-and-exit
  [msg]
  (print-and-exit msg :error true))

(defn try-register-user
  [options]
  (let [{:keys [host username password]} options]
    (if (and host username password)
      (do (api/register! host username password)
          (print-and-exit (format "Registered %s on %s" username host)))
      (error-and-exit "Host, username, and password must be supplied to register"))))

(defn try-update-user
  [options]
  (let [{:keys [host username password]} options]
    (when-not (and host username password)
      (error-and-exit "Need to provide host, username and password to update."))
    (db/set-in-act! host username :pass password)
    (error-and-exit "Updated")))

(defn print-help
  [summary]
  (error-and-exit (clojure.string/join \newline
    ["Usage: lein run [OPTIONS]"
     "Update credentials: lein run --update [OPTIONS]"
     "Registration: lein run -r <OPTIONS>"
     summary])))

(cfg/define
  {:db {:nested {:host {:type :string
                        :required true
                        :description "MongoDb host"}
                 :user {:type :string
                        :required true
                        :description "MongoDb user"}
                 :pass {:type :string
                        :required true
                        :description "MongoDb pass"}
                 :db-name {:type :string
                           :required true
                           :description "MongoDb DB"}}}
   :host {:type :string
          :required true
          :description "Matrix host"}
   :user {:type :string
          :required true
          :description "Matrix username"}
   :pass {:type :string
          :required false
          :description "Matrix password"}
   :log-dir {:description "Path of log file"
             :type :string
             :required false}})

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (when (:help options)
      (print-help summary))
    (if (:config options)
      (do (cfg/populate-from-file (:config options))
          (start #'bananadine.db/dbcon))
      (error-and-exit "No config file specified"))
    (when (:register options)
      (try-register-user options))
    (when (:update options)
      (try-update-user options))
    options))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [options (validate-args args)]
    (mu/start-publisher!
     {:type :custom
      :fqn-function "bananadine.logger/pretty-publisher"
      :filename (cfg/get :log-dir)})
    (start)))
