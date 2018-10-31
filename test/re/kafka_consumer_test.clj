(ns re.kafka-consumer-test
  (:require [integrant.core :as ig]
            [re.kafka-producer :as kafka]
            [clojure.test :refer :all]
            [re.test-utils :as utils :refer [with-system]]))

(def config
  (->
   {:re/kafka-consumer {:topics ["test.consumer"]
                        :configs {"bootstrap.servers" ["kafka:9092"]
                                  "group.id" "test"
                                  "auto.offset.reset" "earliest"}
                        :count 1
                        :pipeline [(ig/ref ::utils/result-stage)]}
    :re/kafka-producer {:configs {"bootstrap.servers" ["kafka:9092"]}}}
   (utils/with-result-stage)))

(deftest basic
  (with-system [system config]
    (kafka/send (:re/kafka-producer system) {:topic "test.consumer"
                                             :key "first"
                                             :value (.getBytes "hello world")})
    (let [[data :as res] (deref (::utils/result system) 30000 ::timeout)]
      (assert (not= ::timeout res))
      (is (= "hello world" (String. (:core/binary-message data)))))))
