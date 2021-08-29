(ns fkcss.cljs
  (:require
    [fkcss.core :as ss]))

(defonce ^:private mounted-styles (atom {}))

(defn- get-style-node [id]
  (or (js/document.getElementById (str id))
    (let [node (js/document.createElement "style")]
      (set! (.-id node) (str id))
      (js/document.body.appendChild node))))

(defn- inject-css [id config]
  (let [node (get-style-node id)]
    (set! (.-innerHTML node) (ss/gen-css config))
    (swap! mounted-styles assoc id {:node node :config config})))

(defn mount!
  ([]
    (mount! "fkcss-styles" {}))
  ([id config]
    (if (some? js/document.body)
      (inject-css id config)
      (js/window.addEventListener "DOMContentLoaded" (partial inject-css id config)))))

(defn ^:private ^:dev/after-load ^:after-load re-gen []
  (doseq [{:keys [node config]} (vals @mounted-styles)]
    (set! (.-innerHTML node) (ss/gen-css config))))

(defn unmount!
  ([]
    (unmount! "fkcss-styles"))
  ([id]
    (let [el (js/document.getElementById (str id))]
      (-> el .-parent (.removeChild el))
      (swap! mounted-styles dissoc id))))