(ns re.metrics
  (:require [integrant.core :as ig])
  (:import io.micrometer.core.instrument.binder.jetty.JettyStatisticsMetrics
           [io.micrometer.core.instrument.binder.jvm JvmGcMetrics JvmMemoryMetrics JvmThreadMetrics]
           io.micrometer.core.instrument.binder.system.ProcessorMetrics
           io.micrometer.core.instrument.Metrics
           [io.micrometer.prometheus PrometheusConfig PrometheusMeterRegistry]
           org.eclipse.jetty.server.handler.StatisticsHandler))

(defmethod ig/init-key :re/metrics [_ _]
  (let [registry (PrometheusMeterRegistry. PrometheusConfig/DEFAULT)]
    (doseq [m [(JvmMemoryMetrics.)
               (JvmGcMetrics.)
               (ProcessorMetrics.)
               (JvmThreadMetrics.)]]
      (.bindTo m registry))
    registry))

(defmethod ig/init-key :re.metrics/jetty [_ {:keys [registry]}]
  (fn [server]
    (let [stats (StatisticsHandler.)]
      (.setHandler stats (.getHandler server))
      (.setHandler server stats)
      (-> (JettyStatisticsMetrics. stats [])
          (.bindTo registry)))))

;; (defmacro with-timer [metric labels & body]
;;   `(with-timer* ~metric ~labels (fn [] ~@body)))
