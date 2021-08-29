(ns fkcss.core-test
  (:require
    #?(:clj [fkcss.core :as ss]
       :cljs [fkcss.core :as ss :require-macros true])
    [fkcss.misc :as ss-misc]
    #?(:clj [clojure.test :refer [deftest is are run-tests testing]]
       :cljs [cljs.test :refer [deftest is are run-tests testing] :include-macros true])))

(deftest gen-css-test
  (binding [ss/*registry* (atom ss/EMPTY-REGISTRY)]
    (ss/defclass thing {:color "red"})
    (let [css (-> (ss/gen-css) ss-misc/reduce-whitespace)]
      (is (= css ".fkcss-core-test-thing { color: red; }"))))
  (binding [ss/*registry* (atom ss/EMPTY-REGISTRY)]
    (ss/defanimation thing {:from {:color "red"} :to {:color "blue"}})
    (let [css (-> (ss/gen-css) ss-misc/reduce-whitespace)]
      (is (= css "@keyframes fkcss-core-test-thing { from { color: red; } to { color: blue; } }"))))
  (binding [ss/*registry* (atom ss/EMPTY-REGISTRY)]
    (ss/reg-font! "SomeFont" {})
    (let [css (-> (ss/gen-css) ss-misc/reduce-whitespace)]
      (is (= css "@font-face { font-family: 'SomeFont'; }")))))