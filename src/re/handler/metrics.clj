(ns re.handler.metrics
  (:require [integrant.core :as ig])
  (:import io.prometheus.client.exporter.common.TextFormat))

(defmethod ig/init-key :re.handler/metrics [_ {:keys [registry]}]
  (fn [_]
    {:status  200
     :headers {"Content-Type" TextFormat/CONTENT_TYPE_004}
     :body (.scrape registry)}))

