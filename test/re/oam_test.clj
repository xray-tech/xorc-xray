(ns re.oam-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [re.oam :as oam]
            [integrant.core :as ig]
            [re.test-utils :as utils :refer [with-system]]
            [re.pipeline :as pipeline]))

(def config
  {:re/oam {}
   :re.oam/compiler {}})

(def basic-code
  "Coeffect({. name = \"dummy\".}) | 2")

(deftest basic
  (with-system [system config]
    (let [{:keys [run unblock]} (:re/oam system)
          program ((:re.oam/compiler system) basic-code)
          {:keys [:oam/values :oam/coeffects :oam/state]}
          (run {} (io/input-stream program))]
      (is (= [2] values))
      (is (= [[0 {"name" "dummy"}]] coeffects))
      (let [{:keys [:oam/values :oam/coeffects :oam/state]}
            (unblock {} {:bc (io/input-stream program)
                         :state (io/input-stream state)
                         :id 0
                         :value 1})]

        (is (= [1] values))
        (is (= [] coeffects))
        (is (nil? state))))))

(def ffc-code "`re.custom`(1,2)")
(deftest ffc
  (with-system [system (assoc-in config
                                 [:re/oam :ffc] {"re.custom" (fn [a b] (+ a b))}) ]
    (let [{:keys [run]} (:re/oam system)
          program ((:re.oam/compiler system) ffc-code)
          {:keys [:oam/values]}
          (run {} (io/input-stream program))]
      (is (= [3] values)))))

(def sync-coeffects-code "Coeffect({. name = \"high-five\", hand = \"right\"  .}) >>
                            Coeffect({. name = \"incr\"  .})")
(deftest sync-coeffects
  (let [coef-impl (fn [data desc]
                    (assert (= desc {"name" "high-five"
                                     "hand" "right"}))
                    [(update data ::number (fnil inc 0)) "five"])
        coef2-impl (fn [data desc]
                     [(update data ::number (fnil inc 0)) "ok"])]
    (with-system [system (assoc-in config
                                   [:re/oam :coeffects]
                                   [["high-five" coef-impl]
                                    ["incr" coef2-impl]]) ]
      (let [program ((:re.oam/compiler system) sync-coeffects-code)
            {:keys [run]} (:re/oam system)
            {:keys [:oam/values ::number]} (run {} (io/input-stream program))]
        (is (= ["ok"] values))
        (is (= number 2))))))

(def stage-code "1 | Coeffect({. name = \"dummy\"  .}) ")
(deftest stage
  (let [config (->
                config
                (assoc :re.stage.oam/run {:oam (ig/ref :re/oam)}
                       :re.stage.oam/unblock {:oam (ig/ref :re/oam)}))
        coef2-impl (fn [data desc] [data "ok"])]
    (with-system [system config]
      (let [program ((:re.oam/compiler system) stage-code)
            [{:keys [:oam/values] :as data}] (pipeline/execute {:oam/bc program} [(:re.stage.oam/run system)])]
        (is (= [1] values))
        (let [data' (assoc data :oam/coeffect-id 0 :oam/coeffect-value 2)
              [{:keys [:oam/values]}] (pipeline/execute data' [(:re.stage.oam/unblock system)])]
          (is (= [1 2] values)))))))
