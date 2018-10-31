(ns re.coeffect.dummy
  (:require [clojure.tools.logging :refer [info]]
            [integrant.core :as ig]))

(defmethod ig/init-key :re.coeffect/dummy [_ _]
  ["dummy"
   (fn [data id desc]
     (info "Dummy coeffect" {:id id
                             :desc desc
                             :data data})
     data)])
