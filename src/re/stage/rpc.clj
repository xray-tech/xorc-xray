(ns re.stage.rpc
  (:require [clojure.edn :as edn]
            [integrant.core :as ig]
            [re.boundary.programs :as programs]
            [re.boundary.states :as states]
            [re.tracing :as tracing]
            [re.utils :as utils]
            [re.rpc :as rpc]))

(tracing/deftag ::request-type "rpc.request")
(tracing/deftag ::response-type "rpc.response")

(defn handle [{:keys [programs states]} {:keys [:core/message] :as data}
              correlation value]
  (let [response-header (get message "header")
        request-header (get response-header "request")
        ctx (-> (get request-header "context") (some-> (tracing/extract-map)))
        span-options (when ctx
                       {:reference ctx
                        :reference-type :follows})]
    (let [[state-id coeffect] (edn/read-string correlation)]
      (if-let [state (states/get states state-id)]
        (let [program (programs/get programs (:program_id state))
              _ (utils/assert program)]
          [(assoc data
                  :core/program program
                  :core/state state
                  :core/state-id (:id state)
                  :oam/bc (:oam program)
                  :oam/state (:state state)
                  :oam/coeffect-id coeffect
                  :oam/coeffect-value value)])
        (do (tracing/log {"event" "unknown_state"
                          "state" state-id})
            [])))))

(defn enter [config {:keys [:core/message] :as data}]
  (let [correlation (get-in message ["header" "request" "correlationId"])]
    (cond
      (= "common.DynamicResponse" (get-in message ["header" "type"]))
      (handle config data correlation (edn/read-string (get message "payload")))

      correlation
      (handle config data correlation message)

      :else [])))

(defmethod ig/init-key :re.stage/rpc [_ {:keys [programs states] :as config}]
  {:enter (partial enter config)})

(defmethod ig/init-key :re.stage.rpc/route [_ {:keys [rpc]}]
  {:enter (fn [{:keys [:core/state-id :oam/coeffect-value :oam/coeffect-id] :as data}]
            [(rpc/dynamic data rpc state-id coeffect-id coeffect-value)])})
