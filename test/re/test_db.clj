(ns re.test-db
  (:require [integrant.core :as ig]
            [qbits.alia :as alia]
            [re.db :as db]))

(defmethod ig/init-key :re/test-db [_ {:keys [keyspace] :or {keyspace "re_test"}}]
  (let [cluster (alia/cluster {:contact-points ["scylladb"]})
        _ (db/create-keyspace cluster keyspace)
        session (alia/connect cluster keyspace)]
    (db/purge session)
    (db/load-schema session)
    {:session session
     :cluster cluster}))

(defmethod ig/halt-key! :re/test-db [_ {:keys [cluster session]}]
  (alia/shutdown cluster))

