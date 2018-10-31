(ns re.decoder
  (:require [integrant.core :as ig]
            [proto-edn.core :as proto]
            [re.tracing :as tracing])
  (:import java.lang.reflect.Method
           router.RoutableMessageOuterClass$RoutableMessage))

(tracing/deftag ::event-type "protobuf.type")

;; Mapping of type names to type parsers
(defn make-types-mapping [classes]
  (->> (for [class classes]
         (let [class-obj (Class/forName class)
               type-name (-> (.getMethod class-obj "getDescriptor" (into-array Class []))
                             (.invoke class-obj (into-array Object []))
                             (.getFullName))
               ^Method parse-method (.getMethod class-obj "parseFrom" (into-array Class [(Class/forName "[B")]))]
           [type-name (fn [data] (.invoke parse-method class-obj (into-array [data])))]))
       (into {})))

(defmethod ig/init-key :re/decoder [_ {:keys [classes]}]
  (let [types-mapping (make-types-mapping classes)]
    (fn [data]
      (let [type (.getType (.getHeader (RoutableMessageOuterClass$RoutableMessage/parseFrom data)))]
        (if-let [parser (get types-mapping type)]
          (proto/to-edn (parser data))
          (throw (ex-info "Unknown message type" {:type type})))))))
