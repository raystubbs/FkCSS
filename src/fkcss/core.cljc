(ns fkcss.core
  (:require
    [clojure.string :as str]
    [fkcss.render :as ss-render]))

(def EMPTY-REGISTRY {:styles {} :fonts {} :animations {}})
(def ^:dynamic *registry* (atom EMPTY-REGISTRY))

(defn reg-style! [reg-key style]
  (swap! *registry* assoc-in [:styles reg-key] style))

(defn reg-font! [font-name font]
  (let [font-spec
        (cond->> font
          (map? font)
          (conj [])
          
          true
          (map #(assoc % :font-family font-name)))]
    (swap! *registry* assoc-in [:fonts font-name] font-spec)))

(defn reg-animation! [animation-name animation-frames]
  (swap! *registry* assoc-in [:animations animation-name] animation-frames))

(defmacro defclass
  ([var-name style]
    (macroexpand `(defclass ~var-name nil ~style)))
  ([var-name doc-string style]
    {:pre [(symbol? var-name) (map? style)]}
    `(let [full-var-name#
           (-> (symbol (str *ns*) (name '~var-name))
             (with-meta
               (cond-> (meta '~var-name)
                 (string? ~doc-string)
                 (assoc :doc ~doc-string))))
           
           class-name#
           (-> full-var-name# str (str/replace #"[^A-Za-z0-9_-]" "-"))]
       (def ~var-name
         (do
           (fkcss.core/reg-style! full-var-name# {class-name# ~style})
           class-name#)))))

(defmacro defanimation
  ([var-name frames]
    (macroexpand `(defanimation ~var-name nil ~frames)))
  ([var-name doc-string frames]
    {:pre [(symbol? var-name) (map? frames)]}
    `(let [full-var-name#
           (-> (symbol (str *ns*) (name '~var-name))
             (with-meta
               (cond-> (meta '~var-name)
                 (string? ~doc-string)
                 (assoc :doc ~doc-string))))

           animation-name#
           (-> full-var-name# str (str/replace #"[^A-Za-z0-9_-]" "-"))]
       (def ~var-name
         (do
           (fkcss.core/reg-animation! animation-name# ~frames)
           animation-name#)))))

(defn gen-css [& {:keys [property-handlers predicates]}]
  (str
    (->> @*registry* :fonts vals
      (map #(ss-render/render-font % :property-handlers property-handlers :predicates predicates))
      str/join)
    (->> @*registry* :animations
      (map #(ss-render/render-animation (key %) (val %)))
      str/join)
    (->> @*registry* :styles vals
      (map #(ss-render/render-style % :property-handlers property-handlers :predicates predicates))
      str/join)))