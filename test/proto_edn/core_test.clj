(ns proto-edn.core-test
  (:require [proto-edn.core :as proto]
            [clojure.test :refer :all])
  (:import protoedn.testing.Test$User))

(deftest user-converts
  (let [m {"name" "Andrew"
           "age" 29
           "hobby" ["beer"]
           "tags" {"hi" "yo"}
           "sex" :male
           "children" [{"name" "Alisa"}]
           "avatar" (.getBytes "aGkK")}]
    (is (= (-> m
               (update "avatar" #(String. %)))
           (-> m
               (proto/merge-and-build (Test$User/newBuilder))
               (proto/to-edn)
               (update "avatar" #(String. %))))))
  (let [m {"login" "liza"
           "sex" :female
           "age" 5
           "avatar" (.getBytes "")}]
    (is (= {"login" "liza"
            "sex" :female
            "age" 5}
           (-> m
               (proto/merge-and-build (Test$User/newBuilder)) (proto/to-edn)
               (dissoc "avatar"))))))




