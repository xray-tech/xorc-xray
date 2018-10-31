(ns re.benchmark
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :refer [info]]
            [integrant.core :as ig]
            [qbits.alia :as alia]
            [qbits.alia.uuid :as uuid]
            [re.kafka-producer :as kafka]
            [re.utils :as utils]))

(defn send-event [kafka topic key val]
  (kafka/send-async kafka {:topic topic
                           :key key
                           :value val}))

(defn make-program [db universe]
  (let [program (uuid/random)]
    (alia/execute (:session db)
                  {:insert :programs
                   :values [[:universe universe]
                            [:id program]
                            [:is_active true]
                            [:oam (utils/file-to-byte-buffer (io/file (io/resource "re/oam/bench.oam")))]]})))

(defmethod ig/init-key :re.benchmark/loader [_ {:keys [kafka db encoder
                                                       topic
                                                       universes
                                                       entities
                                                       subscription-events]}]
  (info "Data loading...")
  (doseq [universe-i (range universes)
          :let [universe (str "bench" universe-i)]]
    (make-program db universe)
    (doseq [entity-i (range entities)
            :let [entity (str "bench-entity" entity-i)
                  key (str universe "|" entity)]]
      (send-event kafka topic key
                  (encoder {"header" {"type" "events.SDKEvent"
                                                        "recipientId" entity
                                                        "createdAt" 0
                                                        "source" "bench"}
                                              "event" {"name" "bench-init"}
                                              "environment" {"appId" universe}}))
      (dotimes [_ subscription-events]
        (send-event kafka topic key
                    (encoder {"header" {"type" "events.SDKEvent"
                                                          "recipientId" entity
                                                          "createdAt" 0
                                                          "source" "bench"}
                                                "event" {"name" "bench-event"}
                                                "environment" {"appId" universe}})))))
  (info "Data loaded"))
