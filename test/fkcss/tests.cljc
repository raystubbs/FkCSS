(ns fkcss.tests
  (:require
    [fkcss.core :as ss]
    #?(:cljs [fkcss.cljs :as ss-cljs])
    #?(:clj [clojure.test :refer [deftest is run-tests testing]]
       :cljs [cljs.test :refer [deftest is run-tests testing] :include-macros true])
    [clojure.string :as str]))

(defn- gen-css-wo-extra-whitespace [config]
  (str/trim (str/replace (ss/gen-css config) #"\s+" " ")))

(defn- test-exact-style-gen
  ([style expected-css]
    (test-exact-style-gen style expected-css {}))
  ([style expected-css config]
    (binding [ss/*context* (ss/new-context)]
      (ss/reg-class :test-class "test-class" style)
      (is (= (gen-css-wo-extra-whitespace config) expected-css)))))

(deftest test-simple-props
  (test-exact-style-gen
    {:color "red"}
    ".test-class { color: red; }"))

(deftest test-record-props
  (test-exact-style-gen
    {:margin {:left "1rem"}}
    ".test-class { margin-left: 1rem; }"))

(deftest test-seq-props
  (test-exact-style-gen
    {:margin ["1rem 2rem 3rem 4rem"]}
    ".test-class { margin: 1rem 2rem 3rem 4rem; }"))

(deftest test-nested-seq-props
  (test-exact-style-gen
    {:box-shadow [["shadow1"]["shadow2"]]}
    ".test-class { box-shadow: shadow1, shadow2; }"))

(deftest test-nested-styles
  (test-exact-style-gen
    {:tag/a {:text-decoration "none"}}
    ".test-class a { text-decoration: none; }"))

(deftest test-query-tests
  (test-exact-style-gen
    {:test/screen-small? {:font-size "16pt"}}
    "@media (max-width: 768px) { .test-class { font-size: 16pt; } }"))

(deftest test-selector-tests
  (test-exact-style-gen
    {:test/hovered? {:background-color "gray"}}
    ".test-class:hover { background-color: gray; }"))

(deftest test-root-selector
  (test-exact-style-gen
    {:color "red"}
    "[data-theme=\"light\"] .test-class { color: red; }"
    {:root-selector "[data-theme=\"light\"]"}))

(defn run-all [& _]
  (run-tests 'fkcss.tests))