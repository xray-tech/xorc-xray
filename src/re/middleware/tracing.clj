(ns re.middleware.tracing
  (:require [integrant.core :as ig]
            [re.tracing :as tracing]))

;; FIXME it should be on top of middleware chain
(defmethod ig/init-key :re.middleware/tracing [_ _]
  (fn [handler]
    (fn [req]
      (tracing/with-span [s "http_handling"]
        (tracing/set-tag s ::tracing/http-method (name (:request-method req)))
        (tracing/set-tag s ::tracing/http-url (:uri req))
        (let [res (handler req)]
          ;; (tracing/set-num-tag s ::tracing/http-status-code (:status res))
          res)))))
