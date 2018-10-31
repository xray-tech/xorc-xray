(ns dev
  (:refer-clojure :exclude [test])
  (:require [clojure.repl :refer :all]
            [fipp.edn :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.java.io :as io]
            [duct.core :as duct]
            [duct.core.repl :as duct-repl]
            [eftest.runner :as eftest]
            [integrant.core :as ig]
            [integrant.repl :refer [clear halt go init prep]]
            [integrant.repl.state :as state :refer [config system]]

            [qbits.alia :as alia]
            [re.kafka-producer :as kafka]
            [qbits.alia.uuid :as uuid]
            [re.utils :as utils]))

(duct/load-hierarchy)

(defn read-config []
  (duct/read-config (io/resource "re/config.dev.edn")))


;; TODO utility function to remove all daemon keys
(defn read-prod-config []
  (-> (duct/read-config (io/resource "re/config.prod.edn"))
      (dissoc :re/kafka-consumer)))

(defn test []
  (eftest/run-tests (eftest/find-tests "test") {:multithread? false}))

(clojure.tools.namespace.repl/set-refresh-dirs "dev/src" "src" "test")

(when (io/resource "local.clj")
  (load "local"))

(defn with-prod []
  (integrant.repl/set-prep! (comp duct/prep read-prod-config)))

(def keys-to-start [:duct/daemon :re/init :re/db])

(integrant.repl/set-prep!
 (fn []
   (-> (read-config)
       (duct/prep keys-to-start))))

;; resume / reset are copypasted here from integrant.repl.state. the only reason
;; for this is not to start all keys from config, but
;; only [:duct/daemon :re/init] descendants

(defn resume []
  (if-let [prep state/preparer]
    (let [cfg (prep)]
      (alter-var-root #'state/config (constantly cfg))
      (alter-var-root #'state/system (fn [sys]
                                       (if sys
                                         (ig/resume cfg sys keys-to-start)
                                         (ig/init cfg keys-to-start))))
      :resumed)
    (throw (Error. "No system preparer function found."))))

(defn reset []
  (integrant.repl/suspend)
  (refresh :after 'dev/resume))

(comment
  (add-program "dev" "re/oam/timer.oam")
  (add-program "dev" "re/oam/subscription.oam"))
(defn add-program [universe path]
  (let [program (uuid/random)]
    (alia/execute (:session (:re/db system))
                  {:insert :programs
                   :values [[:universe universe]
                            [:id program]
                            [:is_active true]
                            [:oam (utils/file-to-byte-buffer (io/file (io/resource path)))]]})
    program))

(comment
  (send-event "dev" "events.SDKEvent" {"header" {"type" "events.SDKEvent"
                                                 "recipientId" "dev-entity"
                                                 "createdAt" 0
                                                 "source" "repl"}
                                       "event" {"name" "dev-event"}
                                       "environment" {"appId" "dev"}})

  (send-event "dev" "events.SDKEvent" {"header" {"type" "events.SDKEvent"
                                                 "recipientId" "dev-entity"
                                                 "createdAt" 0
                                                 "source" "repl"}
                                       "event" {"name" "checkout"
                                                "properties" [{"key" "sum"
                                                               "numberValue" 1000}]}
                                       "environment" {"appId" "dev"}}))
(defn send-event [topic type event]
  (let [encoder (:re/encoder system)
        kafka (:re/kafka-producer system)]
    (kafka/send kafka {:topic topic
                       :key type
                       :value (encoder event)})))
