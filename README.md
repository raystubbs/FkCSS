# FkCSS
Powerful styling without leaving Clojure/ClojureScript - f**k CSS.

:warning: This project is a draft, not ready for widespread use.

```clj
(ns example.view
  (:require
    [fkcss.core :as ss]))

(def button-class
  (ss/class-for
    {:color "white"
     :border {:color "black" :width "1px" :style :solid}
     :font-size "12pt"
 
     :hovered?
     {:background {:color "blue"}}
     
     :screen-small?
     {:font-size "16pt"}
     
     :#before
     {:content "\"\""
      :position "absolute"
      :left 0
      :top 0
      :bottom 0
      :right 0
      :background {:color (ss/rgb 25 25 25)}}}))

(defn- button [{:keys [on-click text]}]
  [:button {:class button-class :on-click on-click :type :button} text)

;; elsewhere
(let [style-el (js/document.createElement "style")]
  (set! (.-innerHTML style-el) (ss/gen-css))
  (js/document.body.appendChild style-el))
```