(ns re.utils
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.logging :refer [error]])
  (:import java.io.FileInputStream
           java.nio.ByteBuffer)
  (:refer-clojure :exclude [assert]))

(defn with-retry* [{:keys [max-timeout] :or {max-timeout 30000}} f]
  (loop [trying 1]
    (let [res (try
                (f)
                (catch Exception e
                  (error e "Error in retry block, retry" {:try trying})
                  ::error))]
      (if (= ::error res)
        (do (Thread/sleep (min max-timeout (* trying 1000)))
            (recur (inc trying)))
        res))))

(s/def ::max-timeout int?)

(s/fdef with-retry
        :args (s/cat :options (s/? (s/keys :opt-un [::max-timeout])) :body (s/+ any?))
        :ret any?)

(defmacro with-retry [& args]
  (let [{:keys [body options]} (s/conform (:args (s/get-spec `with-retry)) args)]
    `(let [f# (fn [] ~@body)]
       (with-retry* ~options f#))))

(defn index-by
  "Returns a map of the elements of `coll` keyed by the result of `f` on each
   element.  The value at each key will be a single element (in contrast to
   `clojure.core/group-by`).  Therefore `f` should generally return an unique
   key for every element - otherwise elements get discarded."
  [f coll]
  (persistent! (reduce #(assoc! %1 (f %2) %2) (transient {}) coll)))

(defn file-to-byte-buffer [file]
  (with-open [is (FileInputStream. file)]
    (let [channel (.getChannel is)
          buffer (ByteBuffer/allocate (.size channel))]
      (.read channel buffer)
      (.flip buffer))))

(defn byte-buffer-as-bytes [^ByteBuffer buf]
  (let [arr (byte-array (.remaining buf))]
    (.get buf arr)
    arr))

(defn fields-as-bytes [m fields]
  (reduce (fn [m' f] (update m' f byte-buffer-as-bytes)) m fields))

(defmacro assert
  "The same as core.assert but instead of AssertionError it throws RuntimeException"
  ([x]
   `(when-not ~x
      (throw (new RuntimeException (str "Assert failed: " (pr-str '~x))))))
  ([x message]
   `(when-not ~x
      (throw (new RuntimeException (str "Assert failed: " ~message "\n" (pr-str '~x)))))))

(defn update-in-when
  "Like update-in but returns m unchanged if key-seq is not present."
  [m key-seq f & args]
  (let [found (get-in m key-seq ::none)]
    (if-not (identical? ::none found)
      (assoc-in m key-seq (apply f found args))
      m)))
