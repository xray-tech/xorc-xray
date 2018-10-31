(ns re.tracing.logging
  (:require [clojure.tools.logging :as logging])
  (:import io.jaegertracing.internal.JaegerSpanContext
           [io.opentracing.contrib.api SpanData SpanObserver TracerObserver]
           io.opentracing.Span
           java.util.Map
           org.slf4j.MDC))

(def log-levels
  {"info" :info
   "trace" :trace
   "debug" :debug
   "warn" :warn
   "error" :error})

;; It's jaeger specific way to receive trace id
;; opentracing specifications still doesn't have standard way to do it
;; see https://github.com/opentracing/specification/issues/24
(defn jaeger-mdc [data]
  (let [ctx (.context ^Span data)]
    (when (instance? JaegerSpanContext ctx)
      (MDC/put "trace" (Long/toHexString (.getTraceId ^JaegerSpanContext ctx)))
      (MDC/put "span" (Long/toHexString (.getSpanId ^JaegerSpanContext ctx))))))

(defn observer [spans-traverse]
  (let [set-tags (fn [current-data]
                   (doseq [data (spans-traverse current-data)
                           [k v] (.getTags data)]
                     (MDC/put k (print-str v))))
        log-span (fn [^SpanData data msg]
                   (jaeger-mdc data)
                   (doseq [[k v] (.getTags data)]
                     (MDC/put k (print-str v)))
                   (logging/debug msg)
                   (MDC/clear))
        span-observer
        (reify SpanObserver
          (onSetOperationName [_ data name])
          (onSetTag [_ data key value])
          (onSetBaggageItem [_ data key value])
          (^void onLog [_ ^SpanData data ^long micros ^Map fields]
           (when-let [msg (or (get fields "message")
                              (get fields "event"))]
             (let [level (log-levels (get fields "severity") :debug)]
               (jaeger-mdc data)
               (doseq [[k v] fields
                       :when (not= k "message")]
                 (MDC/put k (print-str v)))
               (set-tags data)
               (if-let [err-obj (get fields "error.object")]
                 (logging/logp level err-obj msg)
                 (logging/logp level msg))
               (MDC/clear))))
          (^void onLog [_ ^SpanData data ^long micros ^String event]
           (set-tags data)
           (logging/debug event))
          (^void onFinish [_ ^SpanData data ^long micros]
           (log-span data (str (.getOperationName data) "_finished"))))]
    (reify TracerObserver
      (^SpanObserver onStart [_ ^SpanData data]
       (log-span data (.getOperationName data))
        span-observer))))
