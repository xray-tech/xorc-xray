(ns re.player-test
  (:require [clojure.core.async :refer [pipeline]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :as t]
            [duct.core :as duct]
            [integrant.core :as ig]
            [qbits.alia :as alia]
            [qbits.alia.uuid :as uuid]
            [re.boundary.states :as states]
            [re.pipeline :as pipeline]
            [re.service.timeout :as timeout]
            [re.test-utils :as test-utils]
            [re.utils :as utils])
  (:import java.io.PushbackReader
           java.nio.ByteBuffer
           [org.apache.kafka.common.serialization ByteArraySerializer StringSerializer]
           org.apache.kafka.streams.test.ConsumerRecordFactory
           org.apache.kafka.streams.TopologyTestDriver))

(defmethod ig/init-key ::kafka-storage [_ _] (atom {}))

(defmethod ig/init-key ::kafka [_ storage]
  (fn [messages]
    (doseq [[topic key value] messages]
      (swap! storage update topic conj [key value]))))

(def kafka-factory
  (ConsumerRecordFactory. (StringSerializer.)
                          (ByteArraySerializer.)))

(defmethod ig/init-key ::timeout [_ timeout]
  {:enter (fn [{:keys [:core/binary-message :kafka/key :kafka/topic]}]
            (let [record (.create kafka-factory topic key binary-message)]
              (.pipeInput (:driver timeout) record)))})

(defmulti execute-step (fn [_ [name]] name))

(defn get-from-system [ctx k]
  (let [[_ v] (ig/find-derived-1 (::system ctx) k)]
    v))

(defn update-ctx-with-results [ctx results]
  (-> ctx
      (update ::values (fnil into #{}) (mapcat :oam/values results))))

(defmethod execute-step :produce [ctx [_ topic msg]]
  (let [storage (get-from-system ctx ::kafka-storage)
        encoder (get-from-system ctx :re/encoder)]
    (swap! storage update topic conj ["key" (encoder msg)])
    ctx))

(defmethod execute-step :check-states-meta [ctx [_ id expected]]
  (let [states (get-from-system ctx :re.boundary/states)
        actual (:meta (states/get states id))]
    (t/is (= expected actual)))
  ctx)

(def timeout-config
  {:streams-config {"bootstrap.servers" ["kafka:9092"]
                    "application.id"    "timeout-service"
                    "state.dir"         "/tmp/kafka-streams-player"}
   :input-topic "rpc.timer"
   :output-topic "rpc.responses"
   :timers-store-name "timers"
   :correlations-store-name "correlations"})


(defn read-streams-outputs [ctx]
  (let [timeout (get-from-system ctx :re.service/timeout)
        driver (:driver timeout)]
    (loop []
      (when-let [v (.readOutput driver (:output-topic timeout-config))]
        (let [storage (get-from-system ctx ::kafka-storage)]
          (swap! storage update (:output-topic timeout-config) conj [(.key v) (.value v)]))
        (recur)))))

(defmethod execute-step :increment-time [ctx [_ v]]
  (.advanceWallClockTime (:driver (get-from-system ctx :re.service/timeout)) v)
  (read-streams-outputs ctx)
  (update ctx :core/now + v))

(defmethod execute-step :execute [ctx [_ pipeline data]]
  (let [pipeline (get-in ctx [::options :pipelines pipeline])
        pipeline' (map #(let [p (get-from-system ctx %)] (utils/assert p) p) pipeline)
        res (pipeline/execute (merge {:core/now (:core/now ctx)} data)
                              pipeline')]
    (update-ctx-with-results ctx res)))

(defmethod execute-step :add-campaign [ctx [_ universe code]]
  (let [program (uuid/random)
        {:keys [session]} (get-from-system ctx :re/db)
        compiler (get-from-system ctx :re.oam/compiler)]
    (alia/execute session
                  {:insert :programs
                   :values [[:id program]
                            [:oam (ByteBuffer/wrap (compiler code))]]})
    (alia/execute session
                  {:insert :campaigns
                   :values [[:universe universe]
                            [:id (uuid/random)]
                            [:program_id program]
                            [:is_active true]]})
    ctx))

(defmethod execute-step :check-new-values [ctx [_ values]]
  (t/is (= values (::values ctx)))
  (dissoc ctx ::values))

(defmethod timeout/make-driver ::driver [_ topology config]
  (let [driver (TopologyTestDriver. topology config 0)]
    {:driver driver
     :stop   (fn [] (.close driver))}))

(defn load-config []
  (-> (duct/read-config (io/resource "re/base.edn"))
      (duct/prep)))

(defn file-extension [f]
  (last (str/split (.getName f) #"\.")))

(defn scenario-name [f]
  (->> (butlast (str/split (.getName f) #"\."))
       (str/join "-")))

(defn read-resource [p]
  (slurp (io/resource (.getPath (io/file "re/player" p)))))

(defn edn-read-all [f]
  (with-open [reader (PushbackReader. (io/reader f))]
    (loop [acc []]
      (let [res (edn/read {:eof ::eof
                           :readers {'ig/ref ig/ref
                                     're.player/resource read-resource}}
                          reader)]
        (if (= ::eof res)
          acc
          (recur (conj acc res)))))))

(defn load-scenario [f]
  (let [[options & cases] (edn-read-all f)]
    {:name (scenario-name f)
     :options options
     :cases (vec cases)}))

(defn load-scenarios [path]
  (->> (file-seq (io/file (io/resource path)))
       (filter #(and (.isFile %) (= "edn" (file-extension %))))
       (map load-scenario)))

(defn push-message [pipeline ctx topic key value]
  (let [pipeline' (map #(let [p (get-from-system ctx %)] (utils/assert p) p) pipeline)
        res (pipeline/execute {:core/now (:core/now ctx)
                               :core/binary-message value
                               :kafka/key key
                               :kafka/offset (::kafka-offset ctx)
                               :kafka/partition 0
                               :kafka/topic topic}
                              pipeline')]
    (-> ctx
        (update ::kafka-offset (fnil inc 0))
        (update-ctx-with-results res))))

(defn check-kafka [ctx]
  (let [topics @(get-from-system ctx ::kafka-storage)
        _ (reset! (get-from-system ctx ::kafka-storage) {})
        ctx' (reduce (fn [ctx [topic messages]]
                  (let [pipeline-k (get-in ctx [::options :kafka topic])
                        pipeline (get-in ctx [::options :pipelines pipeline-k])
                        _ (utils/assert pipeline)]
                    (reduce (fn [ctx [key value]]
                              (push-message pipeline ctx topic key value))
                            ctx messages)))
                     ctx topics)]
    (if (empty? (mapcat val @(get-from-system ctx ::kafka-storage)))
      ctx'
      (recur ctx'))))

(defn run-case* [config system options steps]
  (loop [[x & xs] steps ctx {:core/now 0
                             ::config config
                             ::options options
                             ::system system}]
    (let [ctx' (execute-step ctx x)
          ctx'' (check-kafka ctx')]
      (when xs
        (recur xs ctx'')))))

(defn run-case [i config options steps]
  (t/testing (:name (meta steps) (format "scenario step #%d" i))
    (test-utils/with-system [system config]
      (run-case* config system options steps))))

(defn run-scenario [{:keys [options cases]}]
  (let [config (-> (load-config)
                   (assoc :re/test-db {}
                          ::kafka-storage {}
                          ::kafka (ig/ref ::kafka-storage)
                          ::timeout (ig/ref :re.service/timeout)
                          :re.service/timeout {:encoder (ig/ref :re/encoder)
                                               :decoder (ig/ref :re/decoder)
                                               :config timeout-config
                                               :driver ::driver})
                   (dissoc :re.effects/kafka)
                   (assoc-in [:re.stage/effects :handlers :kafka] (ig/ref ::kafka))
                   (merge (:config options)))]
    (doseq [[i case] (map-indexed vector cases)]
      (run-case i config options case))))

(t/deftest scenarios
  (doseq [{:keys [name] :as scenario} (load-scenarios "re/player/")]
    (t/testing name
      (run-scenario scenario))))
