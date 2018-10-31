(ns re.decoder-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [re.test-utils :refer [with-system]]
            [proto-edn.core :as proto])
  (:import events.SdkEvent$SDKEvent
           xray.events.Timer$TimerCreate
           xray.events.Timer$TimerExpired))

(def config
  {:re/decoder {:classes ["events.SdkEvent$SDKEvent"
                          "xray.events.Timer$TimerCreate"
                          "xray.events.Timer$TimerExpired"]}})

(deftest basic
  (with-system [system config]
    (let [event {"header" {"type" "events.SDKEvent" "createdAt" 0 "source" "test"}
                 "event" {"name" "hey"}}
          bytes (-> event
                    (proto/merge-and-build (SdkEvent$SDKEvent/newBuilder))
                    (.toByteArray))]
      (is (= ((:re/decoder system) bytes)
             event)))))

(deftest timer
  (with-system [system config]
    (let [event {"header" {"type" "xray.events.TimerCreate"}
                 "expireAt" 100}
          bytes (-> event
                    (proto/merge-and-build (xray.events.Timer$TimerCreate/newBuilder))
                    (.toByteArray))]
      (is (= ((:re/decoder system) bytes)
             event)))))

(deftest timer-expired
  (with-system [system config]
    (let [event {"header" {"type" "xray.events.TimerExpired"
                           "request" {"type" "xray.events.TimerCreate"
                                      "correlationId" "bar"}}}
          bytes (-> event
                    (proto/merge-and-build (xray.events.Timer$TimerExpired/newBuilder))
                    (.toByteArray))]
      (is (= ((:re/decoder system) bytes)
             event)))))
