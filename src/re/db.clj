(ns re.db
  (:require [clojure.tools.logging :refer [warn]]
            [integrant.core :as ig]
            [qbits.alia :as alia]
            [qbits.alia.cluster-options :as alia-copt]
            [re.tracing :as tracing])
  (:import com.datastax.driver.core.Cluster
           io.opentracing.contrib.cassandra.nameprovider.CustomStringSpanName
           io.opentracing.contrib.cassandra.TracingCluster))

(def schema
  ["
CREATE TABLE IF NOT EXISTS programs  (
  id uuid,
  oam blob,
  PRIMARY KEY (id)
)"

   "
CREATE TABLE IF NOT EXISTS states (
  id uuid,
  program_id uuid,
  state blob,
  meta text,
  PRIMARY KEY (id)
)"

   ;; CRM specific
   "
CREATE TABLE IF NOT EXISTS entities (
  universe text,
  id text,
  active_campaigns set<uuid>,
  subscriptions set<text>,
  meta text,
  PRIMARY KEY (universe, id)
)"

"
CREATE TABLE IF NOT EXISTS entity_subscriptions (
  entity_id text,
  state_id uuid,
  coeffect_id int,
  pattern text,
  PRIMARY KEY (entity_id, state_id, coeffect_id)
)"

   "
CREATE TABLE IF NOT EXISTS campaigns (
  universe text,
  id uuid,
  is_active boolean,
  program_id uuid,
  PRIMARY KEY (universe, id)
)"
])

(defn create-keyspace [cluster keyspace]
  (let [session (alia/connect cluster)]
    (try
      (alia/execute session (str "CREATE KEYSPACE IF NOT EXISTS " keyspace " WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};"))
      (finally
        (alia/shutdown session)))))

(defn load-schema [session]
  (doseq [s schema]
    (alia/execute session s)))

(def tables ["programs" "entities" "states" "campaigns" "entity_subscriptions"])
;; TODO more efficient way?
(defn purge [session]
  (doseq [table tables]
    (try
      (alia/execute session (str "DROP TABLE IF EXISTS " table))
      (catch Exception e
        (warn e "Can't drop table" {:table e})))))


(defn map->values [m]
  (for [[k v] m] [k v]))

(defn execute
  ([query {:keys [session]}]
   (alia/execute session query))
  ([query {:keys [session]} values]
   (alia/execute session query {:values values})))

(defmethod ig/init-key :re/db [_ {:keys [cluster keyspace tracer]}]
  (let [span-names (-> (CustomStringSpanName/newBuilder)
                       (.build "cassandra"))
        cluster (-> (Cluster/builder)
                    (alia-copt/set-cluster-options! cluster)
                    (TracingCluster. tracer span-names))
        _ (create-keyspace cluster keyspace)
        session (alia/connect cluster keyspace)]
    (load-schema session)
    {:session session
     :cluster cluster}))

(defmethod ig/halt-key! :re/db [_ {:keys [cluster session]}]
  (alia/shutdown cluster))

