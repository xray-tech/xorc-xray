(ns re.handler.run
  (:require [ataraxy.response :as response]
            [cheshire.generate :as cheshire]
            [integrant.core :as ig]
            [qbits.alia.uuid :as uuid]
            [re.boundary.programs :as programs]
            [re.oam :as oam]
            [re.pipeline :as pipeline]
            [slingshot.slingshot :refer [try+]]))

(cheshire/add-encoder io.xorc.oam.Tuple
                      (fn [c jsonGenerator]
                        (cheshire/encode-seq (.getV c) jsonGenerator)))

(cheshire/add-encoder io.xorc.oam.ConstSignal
                      (fn [c jsonGenerator]
                        (.writeString jsonGenerator "signal")))

(defmethod ig/init-key :re.handler/run [_ {:keys [pipeline]}]
  (fn [req]
    (try+
     (let [results (pipeline/execute {:core/now (System/currentTimeMillis)
                                      :core/code (:body req)}
                                     pipeline)]
       [::response/ok {:values (mapcat :oam/values results)
                       :states (keep #(when (:oam/state %) (:core/state-id %)) results)}])
     (catch [:type ::oam/compilation-failed] {:keys [exit message]}
       [::response/bad-request {:exit exit :message message}]))))
