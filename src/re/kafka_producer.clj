(ns re.kafka-producer
  (:require [integrant.core :as ig]
            [clojure.tools.logging :refer [info]])
  (:import java.util.Map
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]
           [org.apache.kafka.common.serialization ByteArraySerializer StringSerializer])
  (:refer-clojure :exclude [send]))

(defprotocol Kafka
  (send [this record])
  (send-async [this record]))

(defprotocol Producer
  (stop [this]))

(defmethod ig/init-key :re/kafka-producer [_ {:keys [^Map configs]}]
  (let [producer (KafkaProducer.
                  configs
                  (StringSerializer.)
                  (ByteArraySerializer.))]
    (reify
      Kafka
      (send [_ {:keys [topic key value]}]
        @(.send producer (ProducerRecord. topic key value)))
      (send-async [_ {:keys [topic key value]}]
        (.send producer (ProducerRecord. topic key value)))
      Producer
      (stop [_]
          (.close producer)))))

(defmethod ig/halt-key! :re/kafka-producer [_ producer]
  (stop producer))
