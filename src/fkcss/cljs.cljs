(ns fkcss.cljs
  (:require
    [fkcss.core :as ss]))

(defonce ^:private MOUNTED-STYLES (atom {}))

(defn- inject-css [id config]
  (let [node (js/document.createElement "style")]
    (set! (.-id node) (str id))
    (set! (.-innerHTML node) (ss/gen-css config))
    (js/document.body.appendChild node)
    (swap! MOUNTED-STYLES assoc id {:node node :config config})))

(defn mount!
  ([]
    (mount! "fkcss-styles" {}))
  ([id config]
    (if (some? js/document.body)
      (inject-css id config)
      (js/window.addEventListener "DOMContentLoaded" (partial inject-css id config)))))

(defn ^:private ^:dev/after-load ^:after-load re-gen []
  (doseq [{:keys [node config]} (vals @MOUNTED-STYLES)]
    (set! (.-innerHTML node) (ss/gen-css config))))

(defn unmount!
  ([]
    (unmount! "fkcss-styles"))
  ([id]
    (let [el (js/document.getElementById (str id))]
      (-> el .-parent (.removeChild el))
      (swap! MOUNTED-STYLES dissoc id))))