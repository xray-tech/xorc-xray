(ns re.service.timeout-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [re.service.timeout :as timeout]
            [re.test-utils :refer [with-system]]
            [re.rpc :as rpc])
  (:import [org.apache.kafka.common.serialization ByteArraySerializer StringSerializer]
           org.apache.kafka.streams.test.ConsumerRecordFactory
           org.apache.kafka.streams.TopologyTestDriver))

(def timeout-config
  {:streams-config {"bootstrap.servers" ["kafka:9092"]
                    "application.id"    "timeout-service"
                    "state.dir"         "/tmp/kafka-streams-test"}
   :input-topic "test-timer-requests"
   :output-topic "test-timer-output"
   :timers-store-name "timers"
   :correlations-store-name "correlations"})

(defmethod timeout/make-driver :test [_ topology config]
  (let [driver (TopologyTestDriver. topology config 0)]
    {:driver driver
     :stop   (fn [] (.close driver))}))

(def classes ["xray.events.Timer$TimerCreate"
              "xray.events.Timer$TimerCancel"
              "xray.events.Timer$TimerExpired"])

(def config
  {:re/encoder         {:classes classes}
   :re/decoder         {:classes classes}
   :re.service/timeout {:encoder (ig/ref :re/encoder)
                        :decoder (ig/ref :re/decoder)
                        :config  timeout-config
                        :driver  :test}})


(defn read-outputs [driver topic]
  (loop [acc []]
    (if-let [v (.readOutput driver topic)]
      (recur (conj acc v))
      acc)))

(def kafka-factory
  (ConsumerRecordFactory. (StringSerializer.)
                          (ByteArraySerializer.)))

(defn inject-event [system event]
  (let [record (.create kafka-factory (:input-topic timeout-config)
                        "test-key" event)]
    (.pipeInput (:driver (:re.service/timeout system)) record)))

(defn set-timer [system delay corr-id]
  (inject-event system (->> (rpc/make-call corr-id 0
                                          {"header" {"type" "xray.events.TimerCreate"}
                                           "expireAt" (+ 0 delay)})
                            ((:re/encoder system)))))

(defn cancel-timer [system corr-id]
  (inject-event system (->> (rpc/make-call corr-id 0
                                          {"header" {"type" "xray.events.TimerCancel"}})
                            ((:re/encoder system)))))

(defn advance-wall-clock [system ms]
  (.advanceWallClockTime (:driver (:re.service/timeout system)) ms))

(defn get-triggered [system]
  (->> (read-outputs (:driver (:re.service/timeout system))
                     (:output-topic timeout-config))
       (map #((:re/decoder system) (.value %)))
       (map #(get-in % ["header" "request" "correlationId"]))))

(defn correlation [uuid] (pr-str [uuid 0]))

(deftest basic
  (let* [uuid #(str (java.util.UUID/randomUUID))
         uuid1 (uuid) uuid2 (uuid) uuid3 (uuid)]
    (with-system [system config]
      (testing "triggering"
        (set-timer system 1 uuid1)
        (set-timer system 2 uuid2)
        (set-timer system 4 uuid3)
        (advance-wall-clock system 3000)
        (is (= [(correlation uuid1) (correlation uuid2)]
               (get-triggered system)))
        (advance-wall-clock system 2000)
        (is (= [(correlation uuid3)]
               (get-triggered system))))
      (testing "cancellation"
        (set-timer system 5 uuid1)
        (cancel-timer system uuid1)
        (advance-wall-clock system 2000)
        (is (= []
               (get-triggered system))))
      (testing "cancellation after triggering"
        (set-timer system 7 uuid1)
        (advance-wall-clock system 2000)
        (is (= (get-triggered system)
               [(correlation uuid1)]))
        (cancel-timer system uuid1)
        (advance-wall-clock system 2000)))))
