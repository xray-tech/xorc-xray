(ns re.coeffect.timer-service-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [re.test-utils :refer [with-system]]))

(def config
  {:re.coeffect/timer-service {:rpc (ig/ref :re/rpc)}
   :re/rpc {:topic "rpc.timer"
            :encoder (fn [v] v)}})

(deftest basic
  (with-system [system config]
    (let [[name impl] (:re.coeffect/timer-service system)]
      (is (= "rwait" name))
      (is (= {:core/state-id "state1"
              :core/now 1
              :core/effects
              {:kafka
               [["rpc.timer"
                 "state1"
                 {"header"
                  {"type" "xray.events.TimerCreate",
                   "correlationId" "[\"state1\" 0]",
                   "context" nil},
                  "expireAt" 10}]]}}
             (impl {:core/state-id "state1"
                    :core/now 1}
                   0 {"delay" 10000}))))))
