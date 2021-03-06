(ns re.pipeline
  "It specifies a control flow, where execution is running by propagating ctx
  forth and back via pipeline. A pipeline consists of stages, each stage is a
  map with keys :enter and/or :leave. ctx is a map with arbitrary keys.

  :enter is a function of ctx which returns zero or more ctx for further stages.
  If :enter returns zero ctx it means terminations of pipeline and we are
  starting to rewind it
  :leave is a function of ctxs where ctxs are all ctx generated by further
  stages of the pipeline

  Stages are free to assoc/dissoc ctx as well as doing side effects"
  (:require [integrant.core :as ig]))

(comment
  ;; TODO tracing keys & spans
  (tracing/set-tag :re/state (str state))
  (tracing/set-tag :re/program (str (:id program))))

(defn terminate [ctx]
  (assoc ctx ::queue nil))

(defn enter-all [{:keys [::queue] :as ctx}]
  (if (empty? queue)
    [ctx]
    (let [{:keys [enter leave]} (peek queue)
          ctx' (assoc ctx ::queue (pop queue))
          res (if enter
                (let [res (enter ctx')]
                  (if (empty? res)
                    [(terminate ctx)]
                    (mapcat enter-all res)))
                (enter-all ctx'))]
      (if leave
        (leave res)
        res))))

(defn enqueue
  "Dynamically adds stages to the end of pipeline of ctx"
  [ctx stages]
  (update ctx ::queue into stages))

(defn execute
  "Runs ctx through stages"
  [ctx stages]
  (let [queue (into clojure.lang.PersistentQueue/EMPTY stages)]
    (map #(dissoc % ::queue) (enter-all (assoc ctx ::queue queue)))))

