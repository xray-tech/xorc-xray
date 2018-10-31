(ns re.boundary.states
  (:refer-clojure :exclude [get])
  (:require [clojure.edn :as edn]
            [integrant.core :as ig]
            [qbits.alia :as alia]
            [re.db :as db]
            [re.utils :as utils])
  (:import java.nio.ByteBuffer))

(defprotocol States
  (get [this id]))

(defn save-statement [state]
  [{:insert :states
    :values (-> state
                (update :state #(ByteBuffer/wrap %))
                (update :meta pr-str)
                (assoc :state :state))}
   {:state (:state state)}])

(defmethod ig/init-key :re.boundary/states [_ {{:keys [session]} :db}]
  (let [prepared-get
        (->> {:select :states
              :columns :*
              :where {:id :id}
              :limit 1}
             (alia/prepare session))]
    (reify States
      (get [_ id]
        (-> (alia/execute session prepared-get
                          {:values {:id id}})
            (first)
            (some-> (utils/fields-as-bytes [:state])
                    (update :meta edn/read-string)))))))
