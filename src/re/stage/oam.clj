(ns re.stage.oam
  (:require [clojure.java.io :as io]
            [integrant.core :as ig]
            [qbits.alia.uuid :as uuid]
            [re.boundary.programs :as programs]
            [re.boundary.states :as states]
            [re.utils :as utils]))

(defmethod ig/init-key :re.stage.oam/run [_ {:keys [oam]}]
  {:enter (fn [{:keys [:oam/bc :core/state-id] :as data}]
            [(assoc ((:run oam) data (io/input-stream bc))
                    :core/state-id (or state-id
                                       (uuid/random)))])})

(defmethod ig/init-key :re.stage.oam/unblock [_ {:keys [oam]}]
  {:enter (fn [{:keys [:oam/bc :oam/coeffect-id :oam/coeffect-value :oam/state] :as data}]
            [((:unblock oam) data {:bc (io/input-stream bc)
                                   :state (io/input-stream state)
                                   :id coeffect-id
                                   :value coeffect-value})])})

(defmethod ig/init-key :re.stage.oam/load-state [_ {:keys [programs states]}]
  {:enter (fn [{:keys [:core/state-id] :as data}]
            (when-let [state (states/get states state-id)]
              (let [program (programs/get programs (:program_id state))
                    _ (utils/assert program)]
                [(assoc data
                        :core/program program
                        :core/state state
                        :oam/bc (:oam program)
                        :oam/state (:state state))])))})

(defmethod ig/init-key :re.stage.oam/compile-program [_ {:keys [compiler programs]}]
  {:enter (fn [{:keys [:core/code] :as data}]
            (let [bc (compiler code)
                  program (programs/create programs bc)]
              [(assoc data
                      :core/program program
                      :oam/bc bc)]))})
