(ns re.test-utils
  (:require [duct.core :as duct]
            [integrant.core :as ig]))

;; qbits.hayt contains defmethod declarations
(require 'qbits.hayt)

(duct/load-hierarchy)

(defmacro with-system [[binding config] & body]
  `(let [~binding (-> ~config
                      (doto (ig/load-namespaces))
                      (ig/init))]
     (try
       ~@body
       (finally
         (ig/halt! ~binding)))))

(defn with-result-stage
  ([config] (with-result-stage config 1))
  ([config expected]
   (assoc config ::result {} ::result-stage {:result (ig/ref ::result)
                                             :expected expected})))

(defmethod ig/init-key ::result-stage [_ {:keys [result expected]}]
  (let [buffer (atom [])]
    {:enter (fn [data]
              (let [v (swap! buffer conj data)]
                (when (= expected (count v))
                  (deliver result v)))
              [])}))

(defmethod ig/init-key ::result [_ _]
  (promise))
