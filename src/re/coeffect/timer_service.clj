(ns re.coeffect.timer-service
  (:require [integrant.core :as ig]
            [re.effects :as effects]
            [re.rpc :as rpc]))

(defmethod ig/init-key :re.coeffect/timer-service [_ {:keys [rpc]}]
  ["rwait"
   (fn [{:keys [:core/state-id :core/now] :as data} id desc]
     (let [delay (get desc "delay")]
       (rpc/call data rpc state-id id
                 {"header" {"type" "xray.events.TimerCreate"}
                  "expireAt" (quot (+ now delay) 1000)})))])
