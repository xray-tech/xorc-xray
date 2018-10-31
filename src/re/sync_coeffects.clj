(ns re.sync-coeffects
  (:require [integrant.core :as ig]
            [re.tracing :as tracing]
            [re.oam :as oam]
            [clojure.tools.logging :refer [info]]))

(defmethod ig/init-key :re.sync-coeffects/now [_ _]
  ["now"
   (fn [{:keys [:core/now] :as data} _]
     [data now])])

(defmethod ig/init-key :re.sync-coeffects/println [_ _]
  ["println"
   (fn [data {value "value"}]
     (tracing/log {"event" "coeffect_println" "value" value})
     [data oam/signal])])

(defmethod ig/init-key :re.sync-coeffects/trace [_ _]
  ["trace"
   (fn [data {fields "fields"}]
     (tracing/log (merge {"event" "coeffect_tracing"} fields))
     [data oam/signal])])

(defmethod ig/init-key :re.sync-coeffects/clock [_ _]
  ["clock"
   (fn [{:keys [:core/now] :as data} desc]
     [data (/ now 1000.0)])])

(defmethod ig/init-key :re.sync-coeffects/random [_ _]
  ["random"
   (fn [data {bound "bound"}]
     [data (cond
             (int? bound) (long (rand-int bound))
             (double? bound) (rand bound)
             :else (long (rand-int Integer/MAX_VALUE)))])])

(defmethod ig/init-key :re.sync-coeffects/message [_ _]
  ["core.message"
   (fn [{:keys [core/message] :as data} _]
     [data message])])

(defmethod ig/init-key :re.sync-coeffects/ref-register [_ _]
  ["ref.register"
   (fn [{:keys [:core/state-meta] :as data} desc]
     [(assoc-in data [:core/state-meta :core/refs (get desc "id")] (get desc "init"))
      (get desc "id")])])

(defmethod ig/init-key :re.sync-coeffects/ref-deref [_ _]
  ["ref.deref"
   (fn [{:keys [:core/state-meta] :as data} desc]
     [data (get-in state-meta [:core/refs (get desc "id")])])])

(defmethod ig/init-key :re.sync-coeffects/ref-write [_ _]
  ["ref.write"
   (fn [data desc]
     [(assoc-in data [:core/state-meta :core/refs (get desc "id")] (get desc "value"))
      (get desc "value")])])
