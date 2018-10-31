(ns re.stage.decoder
  (:require [integrant.core :as ig]
            [re.tracing :as tracing]
            [re.decoder :as decoder]
            [clojure.edn :as edn]))

(defmethod ig/init-key :re.stage/decoder [_ {:keys [decoder]}]
  {:enter (fn [data]
            (let [decoded (decoder (:core/binary-message data))]
              (if-let [t (get-in decoded ["header" "type"])]
                (do (tracing/set-tag ::decoder/event-type t)
                    [(assoc data :core/message decoded)])
                [(assoc data :core/message decoded)])))})
