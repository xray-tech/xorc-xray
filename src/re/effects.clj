(ns re.effects
  (:require [integrant.core :as ig]
            [qbits.alia :as alia]
            [re.kafka-producer :as kafka]
            [re.utils :as utils]))

(defn enqueue [data k effect]
  (update-in data [:core/effects k] conj effect))

(defmethod ig/init-key :re.effects/db [_ {:keys [session]}]
  (fn [queries]
    (doseq [[query values] queries]
      (alia/execute session query {:values values}))))

;; TODO transactions
(defmethod ig/init-key :re.effects/kafka [_ kafka]
  (fn [messages]
    (doseq [[topic key value] messages]
      (kafka/send kafka {:topic topic
                         :key key
                         :value value}))))
