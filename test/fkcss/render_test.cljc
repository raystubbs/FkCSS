(ns fkcss.render-test
  (:require
    [fkcss.render :as ss-render]
    [fkcss.misc :as ss-misc]
    [clojure.string :as str]
    #?(:clj [clojure.test :refer [deftest is are run-tests testing]]
       :cljs [cljs.test :refer [deftest is are run-tests testing] :include-macros true])))

(deftest resolve-properties-test
  (are [x y] (= (#'fkcss.render/resolve-properties x) y)
    {:margin {:left "1rem" :top "1rem"}}
    {:margin-left "1rem" :margin-top "1rem"}
    
    {:margin-x "1rem"}
    {:margin-left "1rem" :margin-right "1rem"}
    
    {:margin-y "1rem"}
    {:margin-top "1rem" :margin-bottom "1rem"}))

(deftest resolve-selectors-test
  (are [x y] (= (into {} (#'fkcss.render/resolve-selectors x)) y)
    {:div> {}}
    {[] {} [{:tag "div"}] {}}
    
    {:div> {"btn btn-red" {}}}
    {[] {} [{:tag "div"}] {} [{:tag "div" :classes ["btn" "btn-red"]}] {}}
    
    {[:div> :hovered?] {}}
    {[] {} [{:tag "div" :predicates #{(:hovered? ss-render/default-predicates)}}] {}}
    
    {[:div> :button> :before>>] {}}
    {[] {} [{:tag "div"} {:tag "button" :pseudo-el "before"}] {}}))

(deftest render-style-test
  (are [x y] (= (-> x (ss-render/render-style {}) ss-misc/reduce-whitespace) y)
    {:div> {:color "red"}}
    "div { color: red; }"
    
    {[:div> :hovered?] {:color "red"}}
    "div:hover { color: red; }"
    
    {[:div> :before>>] {:color "red"}}
    "div::before { color: red; }"
    
    {[:hoverable? :div>] {:color "red"}}
    "@media (hover: hover) { div { color: red; } }"))

(deftest render-font-test
  (are [x y] (= (-> x (ss-render/render-font {}) ss-misc/reduce-whitespace) y)
    [{:font-family "Test"
      :src "url(none)"}]
    "@font-face { font-family: 'Test'; src: url(none); }"))
