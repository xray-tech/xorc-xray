(ns re.stage.persistence
  (:require [integrant.core :as ig]
            [re.boundary.states :as states]
            [re.effects :as effects]))

(defmethod ig/init-key :re.stage/persistence [_ _]
  {:enter (fn [{:keys [:core/state-id :core/state-meta
                       :core/program :oam/state] :as data}]
            [(if state
               (effects/enqueue data :db (states/save-statement {:id state-id
                                                                 :program_id (:id program)
                                                                 :state state
                                                                 :meta (merge (:core/state state) state-meta)}))
               (effects/enqueue data :db [{:delete :states
                                           :where {:id state-id}}]))])})
