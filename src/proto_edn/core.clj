;; based on com.google.protobuf.util.JsonFormat
(ns proto-edn.core
  (:refer-clojure :exclude [merge assert])
  (:import [com.google.protobuf ByteString Descriptors$Descriptor Descriptors$EnumDescriptor Descriptors$EnumValueDescriptor Descriptors$FieldDescriptor Descriptors$FieldDescriptor$JavaType Descriptors$FieldDescriptor$Type Message Message$Builder MessageOrBuilder NullValue Value]
           java.util.Map))

(defn map-interface? [m]
  (instance? Map m))

(defmacro assert
  "The same as core.assert but instead of AssertionError it throws RuntimeException"
  ([x]
   `(when-not ~x
      (throw (new RuntimeException (str "Assert failed: " (pr-str '~x))))))
  ([x message]
   `(when-not ~x
      (throw (new RuntimeException (str "Assert failed: " ~message "\n" (pr-str '~x)))))))

(defn get-enum [^Descriptors$EnumDescriptor enum-desc v]
  (let [res (.findValueByName enum-desc (name v))]
    (assert res (format "Invalid enum value %s for enum type: %s" v (.getFullName enum-desc)))
    res))

(declare merge)

(defn get-field-value [^Descriptors$FieldDescriptor field v ^Message$Builder builder]
  (if (nil? v)
    (let [t (.getJavaType field)]
      (cond
        (and (= t Descriptors$FieldDescriptor$JavaType/MESSAGE)
             (= (.getFullName (.getMessageType field)) (.getFullName (Value/getDescriptor))))
        (let [value (-> (Value/newBuilder) (.setNullValueValue 0) (.build))]
          (-> (.newBuilderForField builder field)
              (.mergeFrom (.toByteString value))
              (.build)))

        (and (= t Descriptors$FieldDescriptor$JavaType/ENUM)
             (= (.getFullName (.getEnumType field)) (.getFullName (NullValue/getDescriptor))))
        (.findValueByNumber (.getEnumType field) 0)

        :else nil))
    (let [t (.getType field)]
      (cond
        (or (= t Descriptors$FieldDescriptor$Type/INT32)
            (= t Descriptors$FieldDescriptor$Type/SINT32)
            (= t Descriptors$FieldDescriptor$Type/SFIXED32))
        (int v)

        (= t Descriptors$FieldDescriptor$Type/ENUM)
        (get-enum (.getEnumType field) v)

        (= t Descriptors$FieldDescriptor$Type/BYTES)
        (ByteString/copyFrom v)

        (or (= t Descriptors$FieldDescriptor$Type/GROUP)
            (= t Descriptors$FieldDescriptor$Type/MESSAGE))
        (let [sub-builder (.newBuilderForField builder field)]
          (merge v sub-builder)
          (.build sub-builder))

        :else v))))

;; (defn merge-wrapper [v builder]
;;   (let [type (.getDescriptionForType builder)
;;         field (.findFieldByName type "value")
;;         _ (assert field (format "Invalid wrapper type: %s" (.getFullName type)))]
;;     (.setField builder field (get-field-value field v builder))))

(def mergers
  {
   ;; (.getFullName (BoolValue/getDescriptor)) merge-wrapper
   })

(defn get-or-update [atom k updater]
  (if-let [v (get @atom k)]
    v
    (do (swap! atom updater)
        (get @atom k))))

(def field-name-maps (atom {}))

(defn get-field-name-map [^Descriptors$Descriptor descriptor]
  (let [add-field-name (fn [m]
                         (->>
                          (for [^Descriptors$FieldDescriptor field (.getFields descriptor)
                                k [(.getName field)
                                   (.getJsonName field)]]
                            [k field])
                          (into {})
                          (assoc m descriptor)))]
    (get-or-update field-name-maps descriptor add-field-name)))

(defn merge-map-field [^Descriptors$FieldDescriptor field m ^Message$Builder builder]
  (assert (map-interface? m) (format "Expect a map object but found: %s" m))
  (let [type (.getMessageType field)
        key-field (.findFieldByName type "key")
        value-field (.findFieldByName type "value")
        _ (assert (and key-field value-field) (format "Invalid map field: %s" (.getFullName field)))]
    (doseq [[k v] m
            :let [entry-builder (.newBuilderForField builder field)]]
      (.setField entry-builder key-field (get-field-value key-field k entry-builder))
      (.setField entry-builder value-field (get-field-value value-field v entry-builder))
      (.addRepeatedField builder field (.build entry-builder)))))

(defn merge-repeated-field [^Descriptors$FieldDescriptor field v ^Message$Builder builder]
  (assert (seqable? v) (format "Expect seqable but found: %s" v))
  (doseq [v' v]
    (let [value (get-field-value field v' builder)]
      (assert (some? value) (format "Repeated field elements connot be null in field: %s" (.getFullName field)))
      (.addRepeatedField builder field value))))

(defn merge-field [^Descriptors$FieldDescriptor field v ^Message$Builder builder]
  (if (.isRepeated field)
    (assert (= 0 (.getRepeatedFieldCount builder field))
            (format "Field %s has already been set" (.getFullName field)))
    (do
      (assert (not (.hasField builder field))
              (format "Field %s has already been set" (.getFullName field)))
      (let [oneof (.getContainingOneof field)]
        (assert (or (nil? oneof)
                    (nil? (.getOneofFieldDescriptor builder oneof)))
                (let [other (.getOneofFieldDescriptor builder oneof)]
                  (format "Cannot set field %s because another field %s belonging to the same oneof has already been set" (.getFullName field) (.getFullName other)))))))
  ;; we accept nil values as empty repeatable collections
  (when (not (and (.isRepeated field) (nil? v)))
    (cond
      (.isMapField field)
      (merge-map-field field v builder)

      (.isRepeated field)
      (merge-repeated-field field v builder)

      :else
      (when-let [value (get-field-value field v builder)]
        (.setField builder field value)))))

(defn merge-message [m ^Message$Builder builder]
  (assert (map-interface? m) (format "Expect message object but got: %s" m))
  (let [field-name-map (get-field-name-map (.getDescriptorForType builder))]
    (doseq [[k v] m
            :let [field (get field-name-map k)]
            :when field]
      (merge-field field v builder))))

(defn merge [v ^MessageOrBuilder builder]
  (if-let [merger (get mergers (.getFullName (.getDescriptorForType builder)))]
    (merger v builder)
    (merge-message v builder))
  nil)

(defn merge-and-build [v ^Message$Builder builder]
  (merge v builder)
  (.build builder))

(declare to-edn)

(defn to-edn-single [^Descriptors$FieldDescriptor f v]
  (let [t (.getType f)]
    (cond
      (= t Descriptors$FieldDescriptor$Type/ENUM)
      (if (= (.getFullName (.getEnumType f)) "google.protobuf.NullValue")
        nil
        (keyword (.getName ^Descriptors$EnumValueDescriptor v)))

      (= t Descriptors$FieldDescriptor$Type/BYTES)
      (.toByteArray ^ByteString v)

      (or (= t Descriptors$FieldDescriptor$Type/GROUP)
          (= t Descriptors$FieldDescriptor$Type/MESSAGE))
      (to-edn v)

      :else v)))

(defn to-edn-map [^Descriptors$FieldDescriptor f v]
  (let [t (.getMessageType f)
        key-field (.findFieldByName t "key")
        value-field (.findFieldByName t "value")
        _ (assert (and key-field value-field) "Invalid map field")]
    (->> (for [^Message e v
               :let [e-key (.getField e key-field)
                     e-value (.getField e value-field)]]
           [(to-edn-single key-field e-key)
            (to-edn-single value-field e-value)])
         (into {}))))

(defn to-edn-repeated [f v]
  (mapv (partial to-edn-single f) v))

(defn to-edn [^MessageOrBuilder m]
  ;; including optional fields
  ;;
  ;; ^Descriptors$FieldDescriptor field (.getFields (.getDescriptorForType m))
  ;;        :when (not (and (.isOptional field)
  ;;                        (not (.hasField m field))
  ;;                        (or (= (.getJavaType field) Descriptors$FieldDescriptor$Type/MESSAGE)
  ;;                            (.getContainingOneof field))))
  ;;        :let [v (.getField m field)]
  (->>
   (for [[^Descriptors$FieldDescriptor field v] (.getAllFields m)]
     [(.getJsonName field)
      (cond
        (.isMapField field) (to-edn-map field v)
        (.isRepeated field) (to-edn-repeated field v)
        :else (to-edn-single field v))])
   (into {})))
