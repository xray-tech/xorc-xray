(ns re.pipeline-test
  (:require [re.pipeline :as pipeline]
            [clojure.test :refer :all]))


(deftest basic
  (let [p1 [{:enter (fn [ctx] [(assoc ctx :foo 1)])
             :leave (fn [ctxs] (map #(dissoc % :bar) ctxs))}
            {:enter (fn [ctx] [(update ctx :foo inc) (assoc ctx :bar 2)])}]]
    (is (= [{:foo 2} {:foo 1}] (pipeline/execute {} p1)))))

(deftest no-enter
  (let [p1 [{:leave (fn [ctxs] (map #(dissoc % :bar) ctxs))}
            {:enter (fn [ctx] [(assoc ctx :foo 2 :bar 2)])}]]
    (is (= [{:foo 2}] (pipeline/execute {} p1)))))

(deftest dynamic-dispatch
  (let [dynamic-stage {:enter (fn [ctx] [(update ctx :foo #(/ % 2))])}
        p1 [{:enter (fn [ctx] [(-> (assoc ctx :foo 3)
                                   (pipeline/enqueue [dynamic-stage]))])}
            {:enter (fn [ctx] [(update ctx :foo inc)])}]]
    (is (= [{:foo 2}] (pipeline/execute {} p1)))))

(deftest termination
  (let [p1 [{:enter (fn [ctx] [(-> (assoc ctx :foo 2)
                                   (pipeline/terminate))])}
            {:enter (fn [ctx] [(update ctx :foo inc)])}]]
    (is (= [{:foo 2}] (pipeline/execute {} p1))))

  (let [p1 [{:enter (fn [ctx] [(assoc ctx :foo 2)])}
            {:enter (fn [ctx] [])}
            {:enter (fn [ctx] [(update ctx :foo inc)])}]]
    (is (= [{:foo 2}] (pipeline/execute {} p1)))))
