(ns re.encoder
  (:require [integrant.core :as ig]
            [proto-edn.core :as proto])
  (:import java.lang.reflect.Method))

;; Mapping of type names to type builders
(defn make-types-mapping [classes]
  (->> (for [class classes]
         (let [class-obj (Class/forName class)
               type-name (-> (.getMethod class-obj "getDescriptor" (into-array Class []))
                             (.invoke class-obj (into-array Object []))
                             (.getFullName))
               ^Method build-method (.getMethod class-obj "newBuilder" (into-array Class []))]
           [type-name (fn [] (.invoke build-method class-obj (into-array Object [])))]))
       (into {})))

(defmethod ig/init-key :re/encoder [_ {:keys [classes]}]
  (let [types-mapping (make-types-mapping classes)]
    (fn [msg]
      (let [type (get-in msg ["header" "type"])]
        (if-let [builder (get types-mapping type)]
          (.toByteArray (proto/merge-and-build msg (builder)))
          (throw (ex-info "Unknown type for message" {:type type :msg msg})))))))
