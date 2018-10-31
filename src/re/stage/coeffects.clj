(ns re.stage.coeffects
  (:require [integrant.core :as ig]
            [re.tracing :as tracing]))

(defmethod ig/init-key :re.stage/coeffects [_ {:keys [handlers]}]
  (let [handlers' (into {} handlers)]
    {:enter
     (fn [{:keys [:oam/coeffects] :as data}]
       [(reduce (fn [data [id desc]]
                  (if-let [handler (get handlers' (get desc "name"))]
                    (handler data id desc)
                    (throw (ex-info "Unknown coeffect" {:id id
                                                        :desc desc}))))
                (dissoc data :oam/coeffects) coeffects)])}))
