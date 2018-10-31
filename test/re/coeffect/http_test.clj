(ns re.coeffect.http-test
  (:require [clojure.test :refer :all]
            [integrant.core :as ig]
            [re.test-utils :refer [with-system]]))

(def config
  {:re.coeffect/http {:rpc (ig/ref :re/rpc)}
   :re/rpc {:topic "rpc.http"
            :encoder (fn [v] v)}})

(deftest basic
  (with-system [system config]
    (let [[name impl] (:re.coeffect/http system)]
      (is (= "http" name))
      (is (= {:core/state-id "state1",
              :core/effects
              {:kafka
               [["rpc.http"
                 "state1"
                 {"requestType" :GET,
                  "uri" "http://ya.ru",
                  "header"
                  {"type" "http.HttpRequest",
                   "correlationId" "[\"state1\" 0]",
                   "context" nil}}]]}}
             (impl {:core/state-id "state1"} 0 {"requestType" "get"
                                                "uri" "http://ya.ru"}))))))
