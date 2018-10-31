(ns re.service.timeout
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :refer [warn]]
            [integrant.core :as ig]
            [re.rpc :as rpc])
  (:import java.util.concurrent.TimeUnit
           java.util.Properties
           org.apache.kafka.clients.consumer.ConsumerConfig
           [org.apache.kafka.common.serialization Deserializer Serde Serdes Serializer]
           [org.apache.kafka.streams KafkaStreams StreamsBuilder StreamsConfig]
           [org.apache.kafka.streams.kstream Transformer TransformerSupplier]
           [org.apache.kafka.streams.processor PunctuationType Punctuator]
           org.apache.kafka.streams.state.Stores))

(defn kafka-streams-config [{:keys [streams-config]}]
  (doto (Properties.)
    (.put StreamsConfig/APPLICATION_ID_CONFIG (get streams-config "application.id"))
    (.put StreamsConfig/BOOTSTRAP_SERVERS_CONFIG (get streams-config "bootstrap.servers"))
    (.put StreamsConfig/DEFAULT_KEY_SERDE_CLASS_CONFIG (.getClass (Serdes/String)))
    (.put StreamsConfig/DEFAULT_VALUE_SERDE_CLASS_CONFIG (.getClass (Serdes/ByteArray)))
    (.put StreamsConfig/STATE_DIR_CONFIG (get streams-config "state.dir"))
    (.put ConsumerConfig/AUTO_OFFSET_RESET_CONFIG  "earliest")))

(defn get-timers-store [{:keys [streams config]}]
  (.getStateStore streams (:timers-store-name config)))

(defn get-correlations-store [{:keys [streams config]}]
  (.getStateStore streams (:correlations-store-name config)))

(defn add-timer [{:keys [streams] :as ctx} {:keys [id expire-at payload]}]
  (let [key {:expireAt expire-at
             :partition (.partition streams)
             :offset (.offset streams)}]
    (.put (get-timers-store ctx) key payload)
    (.put (get-correlations-store ctx) id key)))

(defn remove-timer [{:keys [streams] :as ctx} id]
  (if-let [key (.get (get-correlations-store ctx) id)]
    (do (.delete (get-timers-store ctx) key)
        (.delete (get-correlations-store ctx) id))
    (warn "Unknown key to cancel" {:id id})))

(defn dequeue-pending-timers [ctx now f]
  (let [t0-key {:expireAt 0 :partition 0 :offset 0}
        now-secs (quot now 1000)
        now-key {:expireAt now-secs :partition 0 :offset 0}
        timers (.range (get-timers-store ctx) t0-key now-key)]
    (try
      (while (.hasNext timers)
        (let [timer (.next timers)
              value (.-value timer)
              key (.-key timer)]
          (f value)
          (.delete (get-timers-store ctx) key)
          (.delete (get-correlations-store ctx) (:correlationId value))))
      (finally (.close timers)))))

(defn transform [{:keys [config decoder] :as ctx} key value]
  (let [msg (decoder value)
        header (get msg "header")
        id (get header "correlationId")]
    (case (get header "type")
      "xray.events.TimerCreate" (add-timer ctx {:id id
                                                :expire-at (get msg "expireAt")
                                                :payload {:header header
                                                          :original-key key}})
      "xray.events.TimerCancel" (remove-timer ctx id))))

(def timer-key-serde
  (reify Serde
    (configure [_ _ _] nil)
    (close [_] nil)
    (serializer [_]
      (reify Serializer
        (configure [_ _ _] nil)
        (serialize [_ _ {expireAt :expireAt partition :partition offset :offset}]
          (.getBytes (format "%016X-%02X-%016X" expireAt partition offset)))
        (close [_] nil)))
    (deserializer [_]
      (reify Deserializer
        (configure [_ _ _] nil)
        (deserialize [_ _ bytes]
          (let [str (String. bytes)
                expireAt (Long/parseLong (.substring str 0 16) 16)
                partition (Integer/parseInt (.substring str 17 19) 16)
                offset (Long/parseLong (.substring str 20 36) 16)]
            {:expireAt expireAt :partition partition :offset offset}))
        (close [_] nil)))))

(def edn-serde
  (reify Serde
    (configure [_ _ _] nil)
    (close [_] nil)
    (serializer [_]
      (reify Serializer
        (configure [_ _ _] nil)
        (serialize [_ _ v]
          (.getBytes (pr-str v)))
        (close [_] nil)))
    (deserializer [_]
      (reify Deserializer
        (configure [_ _ _] nil)
        (deserialize [_ _ bytes]
          (edn/read-string (String. bytes)))
        (close [_] nil)))))

(defn trigger-timer [{:keys [streams encoder]} now {:keys [header original-key]}]
  (let [event {"header" {"type" "xray.events.TimerExpired"}}]
    (.forward streams original-key (encoder (rpc/make-response header event)))))

(defn punctuator [ctx]
  (reify Punctuator
    (punctuate [_ now]
      (dequeue-pending-timers ctx now (partial trigger-timer ctx now)))))

(defn streams-builder [config]
  (doto (StreamsBuilder.)
    (.addStateStore (-> (Stores/persistentKeyValueStore (:timers-store-name config))
                        (Stores/keyValueStoreBuilder timer-key-serde edn-serde)))
    (.addStateStore (-> (Stores/persistentKeyValueStore (:correlations-store-name config))
                        (Stores/keyValueStoreBuilder edn-serde timer-key-serde)))))

(defn make-transformer-supplier [ctx]
  (reify TransformerSupplier
    (get [_]
      (let [ctx' (atom ctx)]
        (reify Transformer
          (init [_ streams]
            (let [_ (swap! ctx' assoc :streams streams)
                  p (punctuator @ctx')]
              (.schedule streams 1000 PunctuationType/WALL_CLOCK_TIME p)))
          (transform [_ key value]
            (transform @ctx' key value)
            nil)
          (close [_] nil))))))

(defn make-topology [{:keys [config] :as ctx}]
  (let [b (streams-builder config)
        requests (.stream b (:input-topic config))
        supplier (make-transformer-supplier ctx)]
    (-> requests
        (.transform supplier (into-array [(:timers-store-name config)
                                          (:correlations-store-name config)]))
        (.to (:output-topic config)))
    (.build b)))

(defmulti make-driver (fn [driver _ _] driver))
(defmethod make-driver :default [_ topology config]
  (let [driver (KafkaStreams. topology config)]
    (.start driver)
    {:stop (fn [] (.close driver 5 TimeUnit/SECONDS))}))

(defmethod ig/init-key :re.service/timeout [_ {:keys [config driver] :as ctx}]
  (let [topology (make-topology ctx)]
    (make-driver driver topology (kafka-streams-config config))))

(defmethod ig/halt-key! :re.service/timeout [_ {:keys [stop]}]
  (when stop (stop)))
