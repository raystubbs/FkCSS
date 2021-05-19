(ns fkcss.cljs
  (:require
    [fkcss.core :as ss]))

(defonce ^:private MOUNTED-STYLES (atom {}))

(defn mount!
  ([]
    (mount! "fkcss-styles" {}))
  ([id config]
    (js/window.addEventListener "DOMContentLoaded"
      (fn []
        (let [node (js/document.createElement "style")]
          (set! (.-id node) id)
          (set! (.-innerHTML node) (ss/gen-css config))
          (js/document.body.appendChild node)
          (swap! MOUNTED-STYLES assoc id {:node node :config config}))))))

(defn ^:private ^:dev/after-load ^:after-load re-gen []
  (doseq [{:keys [node config]} (vals @MOUNTED-STYLES)]
    (set! (.-innerHTML node) (ss/gen-css config))))

(defn unmount!
  ([]
    (unmount! "fkcss-styles"))
  ([id]
    (let [el (js/document.getElementById id)]
      (-> el .-parent (.removeChild el))
      (swap! MOUNTED-STYLES dissoc id))))