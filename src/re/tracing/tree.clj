(ns re.tracing.tree
  (:import io.jaegertracing.internal.JaegerSpanContext
           [io.opentracing.contrib.api SpanData SpanObserver TracerObserver]
           io.opentracing.Span
           java.util.Map))

;; FIXME
;; in logging and metrics we want to use tags from a whole hierarchy of local
;; spans to achieve it we depend here on jaeger implementation to get current
;; and parent spans id.

(defn span-id [data]
  (let [ctx (.context ^Span data)]
    (when (instance? JaegerSpanContext ctx)
      (.getSpanId ^JaegerSpanContext ctx))))

(defn parent-id [data]
  (let [ctx (.context ^Span data)]
    (when (instance? JaegerSpanContext ctx)
      (.getParentId ^JaegerSpanContext ctx))))

(defn traverse [store data]
  (cons data (lazy-seq (when-let [parent (get @store (parent-id data))]
                         (traverse store parent)))))

(defn observer []
  (let [store (atom {})
        span-observer
        (reify SpanObserver
          (onSetOperationName [_ data name])
          (onSetTag [_ data key value])
          (onSetBaggageItem [_ data key value])
          (^void onLog [_ ^SpanData data ^long micros ^Map fields])
          (^void onLog [_ ^SpanData data ^long micros ^String event])
          (onFinish [_ data micros]
           (when-let [id (span-id data)]
             (swap! store dissoc id))))]
    [(reify TracerObserver
       (onStart [_ data]
        (when-let [id (span-id data)]
          (swap! store assoc id data))
        span-observer))
     (partial traverse store)]))
