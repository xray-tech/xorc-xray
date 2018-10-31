(ns re.stage.effects
  (:require [integrant.core :as ig]
            [re.utils :as utils]))

(def into-set (fnil into #{}))

(defmethod ig/init-key :re.stage/effects [_ {:keys [handlers]}]
  {:leave (fn [datas]
            (let [combined (reduce (fn [res data]
                                     (reduce (fn [res [k effects]]
                                               (let [handler (get handlers k)]
                                                 (utils/assert handler)
                                                 (update res handler into-set effects)))
                                             res (:core/effects data)))
                                   {} datas)]
              (doseq [[handler desciptions] combined]
                (handler desciptions))
              (map #(dissoc % :core/effects) datas)))})
