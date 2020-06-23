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

(ns bananadine.logger
  (:require [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]
            [com.brunobonacci.mulog :as µ]
            [com.brunobonacci.mulog.buffer :as rb])
  (:gen-class))

(deftype PrettyPublisher
    [config ^java.io.Writer filewriter buffer]

  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)

  (publish-delay [_]
    500)

  (publish [_ buffer]
      ;; items are pairs [offset <item>]
    (doseq [item (map second (rb/items buffer))]
      ;; print the item
      (pprint item filewriter))
    (.flush filewriter)
    ;; return the buffer minus the published elements
    (rb/clear buffer)))

(defn pretty-publisher
  [config]
  (let [filename (io/file (:filename config))]
    (PrettyPublisher.
     config
     (io/writer filename :append :true)
     (rb/agent-buffer 10000))))
