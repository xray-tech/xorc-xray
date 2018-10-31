(ns re.stage.effects-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [qbits.alia :as alia]
            [qbits.alia.uuid :as uuid]
            [re.effects :as effects]
            [re.pipeline :as pipeline]
            [re.test-utils :as test-utils :refer [with-system]]
            [re.utils :as utils])
  (:import java.nio.ByteBuffer))

(def config
  {:re.stage/effects {:handlers {:db (ig/ref :re.effects/db)
                                 :kafka (ig/ref :re.effects/kafka)}}

   :re.effects/db (ig/ref :re/test-db)
   :re.effects/kafka (ig/ref :re/kafka-producer)

   :re/kafka-producer {:configs {"bootstrap.servers" ["kafka:9092"]}}
   :re/test-db {}})

(deftest db-effects
  (with-system [system config]
    (let [expected-id (uuid/random)]
      (pipeline/execute
       (effects/enqueue {} :db [{:insert :programs
                                 :values {:id expected-id
                                          :oam :oam}}
                                {:oam (ByteBuffer/wrap (.getBytes "hello"))}])
       [(:re.stage/effects system)])
      (let [[{:keys [id oam]} :as programs] (alia/execute (:session (:re/test-db system)) {:select :programs
                                                                                           :columns :*})]
        (is (= 1 (count programs)))
        (is (= expected-id id))
        (is (= "hello" (String. (utils/byte-buffer-as-bytes oam))))))))

(def consumer-config
  {:topics ["test.effects"]
   :configs {"bootstrap.servers" ["kafka:9092"]
             "group.id" "test"
             "auto.offset.reset" "earliest"}
   :count 1
   :pipeline [(ig/ref ::test-utils/result-stage)]})

(deftest kafka-effects
  (with-system [system (-> config
                           (assoc :re/kafka-consumer consumer-config)
                           (test-utils/with-result-stage 2))]
    (let [expected-id (uuid/random)
          ;; to check duplicates removing
          hello-bytes (.getBytes "hello")]
      (pipeline/execute
       (-> {}
           (effects/enqueue :kafka ["test.effects" "key" hello-bytes])
           (effects/enqueue :kafka ["test.effects" "key" hello-bytes])
           (effects/enqueue :kafka ["test.effects" "key2" (.getBytes "hi")]))
       [(:re.stage/effects system)])
      (let [res (deref (::test-utils/result system) 30000 ::timeout)]
        (assert (not= ::timeout res))
        (is (= #{{:core/binary-message "hi"
                  :kafka/topic "test.effects",
                  :kafka/key "key2"}
                 {:core/binary-message "hello",
                  :kafka/topic "test.effects",
                  :kafka/key "key"}}
               (->> res
                    (map #(update (select-keys % [:core/binary-message
                                                  :kafka/topic
                                                  :kafka/key])
                                  :core/binary-message (fn [b] (String. b))))
                    (into #{}))))))))
