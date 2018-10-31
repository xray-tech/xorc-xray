(ns re.rpc
  (:refer-clojure :exclude [cast])
  (:require [integrant.core :as ig]
            [qbits.alia.uuid :as uuid]
            [re.effects :as effects]
            [re.tracing :as tracing]))

(defn make-call [state-id coeffect event]
  (-> event
      (assoc-in ["header" "correlationId"] (pr-str [state-id coeffect]))
      (update-in ["header" "context"] merge (when-let [s (tracing/get-active)]
                                              (tracing/inject-map s)))))

(defn make-cast [event] event)

(defn make-response [request event]
  (assoc-in event ["header" "request"] request))

(defn make-dynamic [state-id coeffect event]
  {"header" {"type" "common.DynamicResponse"
             "request" {"type" "virtual"
                        "correlationId" (pr-str [state-id coeffect])
                        "context" (when-let [s (tracing/get-active)]
                                    (tracing/inject-map s))}}
   "payload" (pr-str event)})

(defn make-and-enqueue [data {:keys [topic encoder]} state-id event]
  (effects/enqueue data :kafka [topic (str state-id) (encoder event)]))

(defn call [data config state-id coeffect event]
  (->> (make-call state-id coeffect event)
       (make-and-enqueue data config state-id)))

(defn cast [data config event]
  (->> (make-cast event)
       (make-and-enqueue data config (str (uuid/random)))))

(defn response [data config request event]
  (let [[state-id] (get request "correlationId")]
    (->> (make-response request event)
         (make-and-enqueue data config state-id))))

(defn dynamic [data config state-id coeffect event]
  (->> (make-dynamic state-id coeffect event)
       (make-and-enqueue data config state-id)))

(defmethod ig/init-key :re/rpc [_ {:keys [topic encoder] :as config}]
  config)
