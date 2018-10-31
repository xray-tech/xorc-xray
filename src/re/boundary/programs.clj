(ns re.boundary.programs
  (:refer-clojure :exclude [get])
  (:require [integrant.core :as ig]
            [qbits.alia :as alia]
            [qbits.alia.uuid :as uuid]
            [re.utils :as utils])
  (:import java.nio.ByteBuffer))

(defprotocol Programs
  (create [this bc])
  (get [this id]))

(defmethod ig/init-key :re.boundary/programs [_ {{:keys [session]} :db}]
  (let [prepared-get
        (->> {:select :programs
              :columns :*
              :where {:id :id}
              :limit 1}
             (alia/prepare session))
        prepared-save
        (->> {:insert :programs
              :values {:id :id
                       :oam :oam}}
             (alia/prepare session))]
    (reify Programs
      (create [_ bc]
        (let [id (uuid/random)]
          (alia/execute session prepared-save
                        {:values {:id id
                                  :oam (ByteBuffer/wrap bc)}})
          {:id id
           :oam bc}))
      (get [_ id]
        (-> (first (alia/execute session prepared-get
                                 {:values {:id id}}))
            (some-> (utils/fields-as-bytes [:oam])))))))
