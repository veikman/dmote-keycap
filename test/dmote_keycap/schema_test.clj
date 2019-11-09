
(ns dmote-keycap.schema-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [dmote-keycap.data :as data]
            [dmote-keycap.schema :as schema]))

(deftest test-inputs
  (testing "empty inputs should be permitted"
    (is (= true (spec/valid? ::schema/keycap-parameters {}))))
  (testing "defaults for interpolation should be permitted"
    (is (= true (spec/valid? ::schema/keycap-parameters data/option-defaults))))
  (testing "deliberate error"
    (is (= false (spec/valid? ::schema/keycap-parameters {:unit-size 1})))))
