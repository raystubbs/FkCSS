(ns fkcss.cljs
  (:require
    [fkcss.core :as ss]))

(defonce ^:private mounted-styles (atom {}))

(defn- get-style-node [id]
  (or (js/document.getElementById (str id))
    (let [node (js/document.createElement "style")]
      (set! (.-id node) (str id))
      (js/document.body.prepend node)
      node)))

(defn- inject-css [id opts]
  (let [node (get-style-node id)]
    (set! (.-innerHTML node) (ss/gen-css opts))
    (swap! mounted-styles assoc id {:node node :opts opts})))

(defn mount!
  ([]
    (mount! "fkcss-styles" {}))
  ([id opts]
    (if (some? js/document.body)
      (inject-css id opts)
      (js/window.addEventListener "DOMContentLoaded" (partial inject-css id opts)))))

(defn ^:private ^:dev/after-load ^:after-load re-gen []
  (doseq [{:keys [node opts]} (vals @mounted-styles)]
    (set! (.-innerHTML node) (ss/gen-css opts))))

(defn unmount!
  ([]
    (unmount! "fkcss-styles"))
  ([id]
    (let [el (js/document.getElementById (str id))]
      (-> el .-parent (.removeChild el))
      (swap! mounted-styles dissoc id))))