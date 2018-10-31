(ns re.main
  (:gen-class)
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.tools.logging :refer [info]]
            [duct.core :as duct]
            [integrant.core :as ig]))

;; qbits.hayt contains defmethod declarations
(require 'qbits.hayt)

(duct/load-hierarchy)

(defn has-daemon? [system]
  (seq (ig/find-derived system :duct/daemon)))

(defn -main [& args]
  (let [keys (or (duct/parse-keys args) [:duct/daemon :re/init])
        config (-> (duct/read-config (io/resource (or (System/getenv "CONFIG_PATH")
                                                      "re/config.dev.edn")))
                   (duct/prep keys))]
    (info "Start with config" config)
    
    ;; FIXME we don't use duct/exec here because we want to halt system even if
    ;; there are no daemons
    (let [system (ig/init config keys)]
      (if (has-daemon? system)
        (do (duct/add-shutdown-hook ::exec #(ig/halt! system))
            (.. Thread currentThread join))
        (ig/halt! system)))))
