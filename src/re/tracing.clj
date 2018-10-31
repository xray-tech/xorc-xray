(ns re.tracing
  (:require [clojure.tools.logging :refer [warn]]
            [integrant.core :as ig]
            [re.tracing.metrics :as metrics]
            [re.tracing.tree :as tree]
            [re.tracing.logging :as logging])
  (:import [io.opentracing References Scope Span]
           io.opentracing.contrib.api.tracer.APIExtensionsTracer
           io.opentracing.contrib.tracerresolver.TracerResolver
           io.opentracing.noop.NoopTracerFactory
           [io.opentracing.propagation Format$Builtin TextMapExtractAdapter TextMapInjectAdapter]
           io.opentracing.tag.Tags
           [java.util HashMap Map]
           sun.util.logging.resources.logging))

(defn tracer []
  (TracerResolver/resolveTracer))

;; to simplify API we store tracer in global atom at initialization stage
(defonce global (atom (NoopTracerFactory/create)))

(defn inject-map
  [^Span span]
  (let [m (HashMap.)
        adapter (TextMapInjectAdapter. m)]
    (.inject @global (.context span) Format$Builtin/TEXT_MAP adapter)
    m))

(defn extract-map
  [m]
  (let [adapter (TextMapExtractAdapter. m)]
    (.extract @global Format$Builtin/TEXT_MAP adapter)))

(defn debug-sample [span]
  (.set (Tags/SAMPLING_PRIORITY) span (int 1)))

(defonce directory (atom {}))

(defn deftag [k v]
  (swap! directory assoc k v))

;; see https://github.com/opentracing/specification/blob/master/semantic_conventions.md#span-tags-table
(deftag ::component "component")
(deftag ::db-instance "db.instance")
(deftag ::db-statement "db.statement")
(deftag ::db-type "db.type")
(deftag ::db-user "db.user")
(deftag ::error "error")
(deftag ::http-method "http.method")
(deftag ::http-status-code "http.status_code")
(deftag ::http-url "http.url")
(deftag ::message-bus-destination "message_bus.destination")
(deftag ::peer-address "peer.address")
(deftag ::peer-hostname "peer.hostname")
(deftag ::peer-ipv4 "peer.ipv4")
(deftag ::peer-ipv6 "peer.ipv6")
(deftag ::peer-port "peer.port")
(deftag ::peer-service "peer.service")
(deftag ::sampling-priority "sampling.priority")
(deftag ::service "service")
(deftag ::span-kind "span.kind")

(deftag ::sampling-priority "sampling.priority")

(deftag :re/state "re.state")
(deftag :re/program "re.program")

(deftag :re.coeffect/type "re.coeffect.type")

(defn resolve-tag ^String [k]
  (or (get @directory k)
      (throw (RuntimeException. (format "Unknown tag %s" k)))))

(defn get-active ^Span []
  (-> (.active (.scopeManager @global))
      (some-> (.span))))

(defmacro with-active [binding & body]
  `(if-let [~binding (get-active)]
     (do ~@body)
     (warn "No active spans")))

(defn set-tag
  ([tag value] (with-active s (set-tag s tag value)))
  ([^Span span tag ^String value]
   (.setTag span (resolve-tag tag) value)))

(defn set-num-tag
  ([tag value] (with-active s (set-num-tag s tag value)))
  ([^Span span tag ^Number value]
   (.setTag span (resolve-tag tag) value)))

(defn set-bool-tag
  ([tag value] (with-active s (set-bool-tag s tag value)))
  ([^Span span tag ^Boolean value]
   (.setTag span (resolve-tag tag) value)))

(def references {:child (References/CHILD_OF)
                 :follows (References/FOLLOWS_FROM)})

(defn set-reference [builder options]
  (let [type (get references (:reference-type options :child))]
    (assert type (format "Unknown reference type %s" (:reference-type options)))
    (.addReference builder type (:reference options))))

(defn make-span
  ([op-name] (make-span op-name {} {}))
  ([op-name options] (make-span op-name {} options))
  ([op-name tags {:keys [reference] :as options}]
   (let [s (-> (.buildSpan @global op-name)
               (cond->
                   reference (set-reference options)))
         s' (reduce (fn [s [k v]]
                      (cond
                        (string? v) (.withTag s (resolve-tag k) ^String v)
                        (number? v) (.withTag s (resolve-tag k) ^Number v)
                        (boolean? v) (.withTag s (resolve-tag k) ^Boolean v)
                        :else (do (warn "Unsupported tag type" k v)
                                  s)))
                    s
                    tags)]
     (.start s'))))

(defn log
  ([fields]
   (with-active s (log s fields)))
  ([^Span span ^Map fields]
   (.log span fields)))

(defn error
  ([e]
   (with-active s (error s e {})))
  ([e fields]
   (error (get-active) e fields))
  ([span e fields]
   (log span (assoc fields
                    "severity" "error"
                    "event" "error"
                    "error.object" e))))

(defn activate-span ^Span [span]
  (.activate (.scopeManager @global) span true))

(defn close-scope [^Scope scope]
  (.close scope))

(defmacro with-span [[binding op-name & [tags options]] & body]
  `(let [~binding (make-span ~op-name ~tags ~options)
         scope# (activate-span ~binding)]
     (try
       ~@body
       (catch Exception e#
         (error ~binding e# {})
         (set-bool-tag ~binding ::error true)
         (throw e#))
       (finally
         (close-scope scope#)))))

(defmethod ig/init-key :re/tracing [_ {:keys [metrics metric-tags metric-summaries]}]
  (let [[tree-observer spans-traverse] (tree/observer)
        t (-> (tracer)
              (APIExtensionsTracer.)
              (doto
                  (.addTracerObserver
                   (metrics/observer metrics spans-traverse metric-tags metric-summaries))
                (.addTracerObserver (logging/observer spans-traverse))
                (.addTracerObserver tree-observer)))]
    (reset! global t)
    t))
