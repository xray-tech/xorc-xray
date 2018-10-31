(ns re.oam
  (:require [clojure.java.shell :as shell]
            [clojure.tools.logging :refer [warn]]
            [integrant.core :as ig]
            [re.tracing :as tracing])
  (:import [io.xorc.oam BCDeserializer Coeffect ConstSignal Context Deserializer Inter PrimsKt Serializer Snapshot UnknownCoeffect]
           java.io.InputStream
           kotlin.jvm.functions.Function1))

(def signal ConstSignal/INSTANCE)

(defn make-result [^Snapshot res]
  (doseq [v (seq (.getValues res))]
    (tracing/log {"event" "oam_value"
                  "value" v}))
  {:values (seq (.getValues res))
   :coeffects (->> (.getCoeffects res)
                   (map (fn [^Coeffect c] [(.getId c) (.getDescription c)])))
   :state (when (.isRunning (.getState res))
            (.getState res))})

(tracing/deftag ::type "oam.run-type")

(defn run-loop [coeffects inter data res]
  (loop [data' data
         [[id desc :as coeff] & xs] (:coeffects res)
         state (:state res)
         values (:values res)
         async-coeffects []]
    (if (and id state)
      (if-let [handler (get coeffects (get desc "name"))]
        (let [[data'' res]
              (tracing/with-span [_ "oam" {::type "sync-coeffect"
                                           :re.coeffect/type (get desc "name")}]
                (let [[data'' v'] (handler data' desc)]
                  (tracing/log {"event" "coeffect_result"
                                "id" id
                                "value" v'})
                  [data'' (-> inter
                              (.unblock state id v')
                              (make-result))]))]
          (recur data''
                 (concat xs (:coeffects res))
                 (:state res)
                 (concat values (:values res))
                 async-coeffects))
        (recur data' xs state values (conj async-coeffects coeff)))
      (-> data'
          (update :oam/values concat values)
          (update :oam/coeffects concat async-coeffects)
          (assoc :oam/state (when state
                              (.serialize (Serializer. state))))))))

(defn run [coeffects data ^InputStream input-stream]
  (try
    (let [bc (.deserialize (BCDeserializer. input-stream))
          inter (Inter. bc)]
      (tracing/with-span [_ "oam" {::type "run"}]
        (-> (.run inter)
            (make-result)
            (->> (run-loop coeffects inter data)))))
    (finally
      (.close input-stream))))

(defn unblock [coeffects data {:keys [^InputStream bc ^InputStream state id value]}]
  (try
    (let [bc (.deserialize (BCDeserializer. bc))
          instance (.deserialize (Deserializer. state))]
      (try
        (let [inter (Inter. bc)]
          (tracing/with-span [_ "oam" {::type "unblock"}]
            (-> (.unblock inter instance id value)
                (make-result)
                (->> (run-loop coeffects inter data)))))
        (catch UnknownCoeffect _
          (warn "Unknown coeffect" {:data data :id id :value value}))))
    (finally
      (.close bc)
      (.close state))))

(defmethod ig/init-key :re/oam [_ {:keys [ffc coeffects]}]
  (PrimsKt/primsLoad)
  (let [ctx (Context/getInstance)]
    (doseq [[name ffc] ffc]
      (.set ctx name (reify Function1
                       (invoke [_ args] (apply ffc args))))))
  (let [coeffects' (into {} coeffects)]
    {:run (partial run coeffects')
     :unblock (partial unblock coeffects')}))

(defmethod ig/init-key :re.oam/compiler [_ {:keys [path prelude]
                                            :or {path "orc.exe"
                                                 prelude "/orc/prelude"}}]
  (fn [orc]
    (let [res (shell/sh
               path "compile" "-i" prelude
               :in orc
               :out-enc :bytes)]
      (if (= 0 (:exit res))
        (:out res)
        (throw (ex-info "Compilation failed" {:type ::compilation-failed
                                              :exit (:exit res)
                                              :message (String. (:err res))}))))))
