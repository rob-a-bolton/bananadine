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
  (:require [bananadine.matrix.api :as api]
            [bananadine.db :as db]
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

(defn warn-invalid-cmd
  [channel cmd-name]
  (api/msg-room! channel [:p "Command " [:strong cmd-name] " not found"]))

(defn as-cmd
  [s]
  (let [[cmd s] (str/split s #" " 2)]
    [(subs cmd (count (cmd-str))) (or s "")]))

(defn match-cmd
  [cmd-str cmd-def]
  (some #(when (.startsWith cmd-str (:cmd %)) %)
        (sort #(> (count (:cmd %1)) (count (:cmd %2))) (:cmds cmd-def))))

(defn convert-or-pass-args
  [arg-defs arg-list]
  (map (fn [arg-def arg] ((or (:converter arg-def) identity) arg))
       arg-defs
       arg-list))

(defn parse-cmd
  [channel cmd-str cmd-def]
  (if-let [cmd (match-cmd cmd-str cmd-def)]
    (let [arg-str (str/trim (subs cmd-str (count (:cmd cmd))))
          num-args (count (:args cmd))
          arg-list (str/split arg-str #" " num-args)
          arg-list (if (= arg-list [""]) [] arg-list)
          converted-args (convert-or-pass-args (:args cmd) arg-list)]
      (if (= num-args (count converted-args))
        (apply (:handler cmd) (cons channel converted-args))
        (api/msg-room! channel
                       [:p
                        "Command "
                        [:strong (:cmd cmd)]
                        " requires "
                        [:strong num-args]
                        " args, but you supplied "
                        [:strong (count converted-args)]])))
    (warn-invalid-cmd channel (format "%s %s" (:name cmd-def) cmd-str))))

(defn subcmd-help-str
  [cmd-name sub-cmd]
  [[:strong (str (str/trimr (str cmd-name " " (:cmd sub-cmd))) ": ")]
   [:em (str/join " " (map (comp name :name) (:args sub-cmd)))]
   (if-let [desc (:desc sub-cmd)] (str " " desc) "")])

(defn cmd-help-str
  [cmd-def]
  [:p
   [:h3 (:name cmd-def)]
   [:caption (:desc cmd-def)]
   [:br]
   (->> (map (partial subcmd-help-str (:name cmd-def)) (:cmds cmd-def))
        (interpose [[:br]])
        (apply concat))])

(defn cmd-brief-help-str
  [cmd-def]
  [[:strong (:name cmd-def)] ": " (:desc cmd-def)])

(defn try-fallbacks
  [channel cmd-name subcmd-str]
  (some (fn [f] (f channel cmd-name subcmd-str)) (get @command-atom :fallbacks)))

(defn process-cmd-str
  [channel cmd-name subcmd-str]
  (if-let [cmd-def (get-in @command-atom [:cmds cmd-name])]
    (parse-cmd channel subcmd-str cmd-def)
    (when-not (try-fallbacks channel cmd-name subcmd-str)
      (warn-invalid-cmd channel cmd-name))))

(defn try-extract-cmd
  [event]
  (let [s (get-in event [:msg :plain])
        channel (:channel event)]
    (when-let [[cmd-name subcmd-str] (as-cmd s)]
      (process-cmd-str channel cmd-name subcmd-str))))

(defn register-fallback-handler!
  [handler]
  (let [fallback-handlers (get @command-atom :fallbacks)]
    (swap! command-atom assoc :fallbacks (conj (set fallback-handlers) handler))))

(defn unregister-fallback-handler!
  [handler]
  (let [fallback-handlers (get @command-atom :fallbacks)]
    (swap! command-atom assoc :fallbacks (disj (set fallback-handlers) handler))))

(defn register-cmd!
  [cmd-def]
  (swap! command-atom assoc-in [:cmds (:name cmd-def)] cmd-def))

(defn unregister-cmd!
  [cmd-def]
  (let [new-cmds (dissoc (:cmds @command-atom) (:name cmd-def))]
    (swap! command-atom assoc :cmds new-cmds)))

(defn list-modules
  [channel]
  (let [modules (reverse (sort-by :name (get @command-atom :cmds)))
        module-infos (map (fn [[_ mod-def]] (cmd-brief-help-str mod-def) ) modules)]
    (api/msg-room! channel
                   [:p (apply concat (interpose [[:br]] module-infos))])))

(defn list-module-cmds
  [channel module-name]
  (if-let [module-def (get-in @command-atom [:cmds module-name])]
    (api/msg-room! channel (cmd-help-str module-def))
    (warn-invalid-cmd channel module-name)))

(def help-cmd-def
  {:name "help"
   :desc "Help module, provides info about commands. Use ~help cmd <module> for more info"
   :cmds [{:cmd "list" :args [] :desc "List available command modules" :handler list-modules}
          {:cmd "" :args [{:name :name}] :desc "List subcommands for module" :handler list-module-cmds}]})

(defn start-command-state!
  []
  (util/add-hook! event-state
                  :m.text
                  {:handler try-extract-cmd})
  (register-cmd! help-cmd-def)
  command-atom)

(defn stop-command-state!
  []
  (util/rm-hook! event-state :m.text try-extract-cmd)
  (reset! command-atom {}))

(defstate command-state
  :start (start-command-state!)
  :stop (stop-command-state!))
