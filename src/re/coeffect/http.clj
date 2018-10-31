(ns re.coeffect.http
  (:require [clojure.string :as str]
            [integrant.core :as ig]
            [re.pipeline :as effects]
            [re.rpc :as rpc]
            [re.utils :as utils]))
(defmethod ig/init-key :re.coeffect/http [_ {:keys [rpc]}]
  ["http"
   (fn [{:keys [:core/state-id] :as data} id desc]
     (rpc/call data rpc state-id id
               (-> (into {} desc)
                   (utils/update-in-when ["requestType"] (comp keyword str/upper-case))
                   (assoc "header" {"type" "http.HttpRequest"}))))])
