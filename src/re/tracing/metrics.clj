(ns re.tracing.metrics
  (:import [io.micrometer.core.instrument Counter DistributionSummary Timer]
           [io.opentracing.contrib.api SpanData SpanObserver TracerObserver]
           java.util.concurrent.TimeUnit
           java.util.Map))

(defn observer [metrics spans-traverse tags-to-record summaries]
  (let [registry (atom {})
        gather-tags (fn [data]
                      (reduce (fn [res data]
                                (into res (.getTags data)))
                              {} (spans-traverse data)))
        get-or-create-meter (fn [name tags builder]
                              (if-let [m (get @registry [name tags])]
                                m
                                (let [m (builder name)]
                                  (doseq [[k v] tags]
                                    (.tag m k (print-str v)))
                                  (.register m metrics))))
        on-log (fn [data event fields]
                 (let [tags (-> (gather-tags data)
                                (merge fields)
                                (select-keys (get tags-to-record event))
                                (assoc "operation" (.getOperationName data)))]
                   (if-let [summary (some->> (get summaries event)
                                             (get fields))]
                     (-> (get-or-create-meter event
                                              tags
                                              #(DistributionSummary/builder %))
                         (.record summary))
                     (-> (get-or-create-meter event
                                              tags
                                              #(Counter/builder %))
                         (.increment)))))
        span-observer
        (reify SpanObserver
          (onSetOperationName [_ data name])
          (onSetTag [_ data key value])
          (onSetBaggageItem [_ data key value])
          (^void onLog [_ ^SpanData data ^long micros ^Map fields]
           (when-let [e (get fields "event")]
             (on-log data e fields)))
          (^void onLog [_ ^SpanData data ^long micros ^String event]
           (on-log data event {}))
          (onFinish [_ data micros]
            (let [op (.getOperationName data)
                  tags (-> (gather-tags data)
                           (select-keys (get tags-to-record op)))]
              (-> (get-or-create-meter op tags
                                       #(Timer/builder %))
                  (.record (.getDuration data) TimeUnit/MICROSECONDS)))))]
    (reify TracerObserver
      (onStart [_ data] span-observer))))
