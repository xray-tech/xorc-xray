(ns re.service.http
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [integrant.core :as ig]
            [re.effects :as effects]
            [re.rpc :as rpc]
            [re.tracing :as tracing]))

(defmethod ig/init-key :re.service/http [_ {:keys [rpc]}]
  (fn [{:keys [:core/message] :as data}]
    (let [header (get message "header")
          ctx (tracing/extract-map (get header "context"))
          span-options (when ctx
                         {:reference ctx
                          :reference-type :follows})]
      (tracing/with-span [s "http_request" {} span-options]
        (let [req {:method (-> (get message "requestType")
                               (name)
                               (str/lower-case)
                               (keyword))
                   :headers (get message "headers")
                   :query-params (get message "params")
                   :url (get message "uri")
                   :body (get message "body")
                   :as :string
                   :throw-exceptions false}
              _ (do (tracing/set-tag ::tracing/http-method (name (:method req)))
                    (tracing/set-tag ::tracing/http-url (:url req)))
              ;; ;; FIXME handle connection errors
              res (http/request req)
              event {"header" {"type" "http.HttpResponse"}
                     "payload" {"statusCode" (:status res)
                                "responseBody" (apply str (map char (:body res)))
                                "headers" (:headers res)}}
              effect (rpc/response rpc header event)]
          (tracing/set-num-tag ::tracing/http-status-code (:status res))
          (effects/enqueue data :kafka effect))))))
