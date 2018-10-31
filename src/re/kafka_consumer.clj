(ns re.kafka-consumer
  (:require [clojure.tools.logging :refer [error]]
            [integrant.core :as ig]
            [re.pipeline :as pipeline]
            [re.tracing :as tracing]
            [re.utils :as utils])
  (:import [java.util Collection Map]
           [org.apache.kafka.clients.consumer ConsumerRecord KafkaConsumer]
           org.apache.kafka.common.errors.WakeupException
           [org.apache.kafka.common.serialization ByteArrayDeserializer StringDeserializer]))

(defn consumer-loop [group running? ^KafkaConsumer consumer ^Collection topics pipeline]
  (.subscribe consumer topics)
  (try
    (while @running?
      (let [res (.poll consumer 100)]
        (doseq [^ConsumerRecord record res]
          (utils/with-retry
            (when @running?
              (tracing/with-span [_ "kafka_consuming"
                                  {::tracing/span-kind "consumer"
                                   ::tracing/message-bus-destination (.topic record)}]
                ;; maybe :core/now should be time of message creation
                (pipeline/execute {:core/now (System/currentTimeMillis)
                                   :core/binary-message (.value record)
                                   :kafka/key (.key record)
                                   :kafka/offset (.offset record)
                                   :kafka/partition (.partition record)
                                   :kafka/topic (.topic record)}
                                  pipeline)))))))
    (catch WakeupException e
      (when @running?
        (throw e)))
    (finally
      (.close consumer))))

(defn start-consumer [topics ^Map configs handler]
  (let [running? (atom true)
        waiter (promise)
        consumer (KafkaConsumer. configs
                                 (StringDeserializer.)
                                 (ByteArrayDeserializer.))]
    (future
      (while @running?
        (try
          (consumer-loop (configs "group.id") running? consumer topics handler)
          (catch Exception e
            (error e "Error in kafka consumer" {:topics topics}))))
      (deliver waiter nil))
    (fn []
      (reset! running? false)
      (.wakeup consumer)
      @waiter)))

(defmethod ig/init-key :re/kafka-consumer [_ {:keys [count topics configs pipeline]}]
  (let [stops (mapv (fn [_] (start-consumer topics configs pipeline)) (range count))]
    {:stop (fn []
             (doseq [stop stops]
               (stop)))}))

(defmethod ig/halt-key! :re/kafka-consumer [_ {:keys [stop]}]
  (stop))
