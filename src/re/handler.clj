(ns re.handler
  (:require [ataraxy.response :as response]
            [integrant.core :as ig]))

(defmethod ig/init-key :re.handler/health [_ _]
  (fn [_]
    [::response/ok "ok"]))
