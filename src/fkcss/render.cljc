(ns fkcss.render
  (:require
    [clojure.string :as str]
    [fkcss.misc :refer [panic]]))

(declare ^:dynamic ^:private *context*)

(def default-property-handlers
  "Custom property handling, includes vendor prefixing."
  {:margin-x
   (fn [v]
     {:props
      {:margin-left v :margin-right v}})

   :margin-y
   (fn [v]
     {:props
      {:margin-top v :margin-bottom v}})

   :padding-x
   (fn [v]
     {:props
      {:padding-left v :padding-right v}})

   :padding-y
   (fn [v]
     {:props
      {:padding-top v :padding-bottom v}})

   :border-top-radius
   (fn [v]
     {:props
      {:border-top-left-radius v
       :border-top-right-radius v}})

   :border-bottom-radius
   (fn [v]
     {:props
      {:border-bottom-left-radius v
       :border-bottom-right-radius v}})

   :border-right-radius
   (fn [v]
     {:props
      {:border-top-right-radius v
       :border-bottom-right-radius v}})

   :border-left-radius
   (fn [v]
     {:props
      {:border-top-left-radius v
       :border-bottom-left-radius v}})

   :box-shadow
   (fn box-shadow-handler [v]
     (cond
       (string? v)
       v

       (map? v)
       (let [{:keys [offset-x offset-y blur-radius spread-radius color inset?]} v]
         (str
           (when inset?
             "inset ")
           (or offset-x 0) " "
           (or offset-y 0) " "
           (when (some? blur-radius)
             (str blur-radius " "))
           (when (some? spread-radius)
             (str spread-radius " "))
           (or color "black")))

       (seqable? v)
       (str/join ", " (map box-shadow-handler v))

       :else
       (str v)))

   :background-clip
   (fn [v]
     {:props
      (case (name v)
        "text"
        {:-webkit-background-clip v}

        #_else
        {:background-clip v})})

   :box-reflect
   (fn [v]
     {:props
      {:-webkit-box-reflext v
       :box-reflect v}})

   :filter
   (fn [v]
     {:props
      {:-webkit-filter v
       :filter v}})

   :display
   (fn [v]
     {:props
      (case (name v)
        "flex"
        {:wk2/display "-webkit-box"
         :wk/display "-webkit-flexbox"
         :ms/display "-ms-flexbox"
         :display "flex"}

        "grid"
        {:ms/display "-ms-grid"
         :display "grid"}

        #_else
        {:display v})})

   :flex
   (fn [v]
     {:props
      {:-webkit-box-flex v
       :width "10%" ; for old syntax, otherwise collapses (from shouldiprefix.com#flexbox)
       :-webkit-flex v
       :-ms-flex v
       :flex v}})

   :font-feature-settings
   (fn [v]
     {:props
      {:-webkit-font-feature-settings v
       :-moz-font-feature-settings v
       :font-feature-settings v}})

   :hyphens
   (fn [v]
     {:props
      {:-webkit-hyphens v
       :-moz-hyphens v
       :-ms-hyphens v
       :hyphens v}})

   :word-break
   (fn [v]
     {:props
      {:-ms-word-break v
       :word-break v}})

   :mask-image
   (fn [v]
     {:props
      {:-webkit-mask-image v
       :mask-image v}})

   :column-count
   (fn [v]
     {:props
      {:-webkit-column-count v
       :-moz-column-count v
       :-column-count v}})

   :column-gap
   (fn [v]
     {:props
      {:-webkit-column-gap v
       :-moz-column-gap v
       :column-gap v}})

   :column-rule
   (fn [v]
     {:props
      {:-webkit-column-rule v
       :-moz-column-rule v
       :column-rule v}})

   :object-fit
   (fn [v]
     {:props
      {:-o-object-fit v
       :object-fit v}})

   :transform
   (fn [v]
     {:props
      {:-webkit-transform v
       :-ms-transform v
       :transform v}})

   :appearance
   (fn [v]
     {:props
      {:-webkit-appearance v
       :-moz-appearance v
       :appearance v}})

   :font-family
   (fn font-family-handler [v]
     {:props
      {:font-family
       (cond
         (string? v)
         (if (or (str/includes? v "'") (str/includes? v "\"") (str/includes? v ",") (not (re-find #"\W" v)))
           v
           (str "'" v "'"))
         
         (sequential? v)
         (str/join ", " (map font-family-handler v))
         
         :else
         (str v))}})})

(def default-predicates
  (merge
    {:hovered? {:selector ":hover"}
     :active? {:selector ":active"}
     :focused? {:selector ":focus"}
     :focus-visible? {:selector ":focus-visible"}
     :enabled? {:selector ":enabled"}
     :disabled? {:selector ":disabled"}
     :visited? {:selector ":visited"}
     :checked? {:selector ":checked"}
     :expanded? {:selector "[aria-expanded=\"true\"]"}
     :current? {:selector "[aria-current]"}
     :screen-tiny? {:query "@media (max-width: 480px)"}
     :screen-small? {:query "@media (max-width: 768px)"}
     :screen-large? {:query "@media (min-width: 1025px)"}
     :screen-huge? {:query "@media (min-width: 1200px)"}
     :pointer-fine? {:query "@media (pointer: fine)"}
     :pointer-coarse? {:query "@media (pointer: coarse)"}
     :pointer-none? {:query "@media (pointer: none)"}
     :hoverable? {:query "@media (hover: hover)"}}
    #?(:cljs
       {:touchable?
        {:exec
         (fn is-touch-device? []
           (or
             (js-in js/window "ontouchstart")
             (< 0 js/navigator.maxTouchPoints)
             (< 0 js/navigator.msMaxTouchPoints)))}})))

(def ^:private ^:dynamic *context*
  {:property-handlers default-property-handlers
   :predicates default-predicates})

(defn- resolve-properties [props]
  (into {}
    (mapcat
      (fn [[prop-key prop-val]]
        (or
          (when-let [handler (get-in *context* [:property-handlers prop-key])]
            (:props (handler prop-val)))
          (when (map? prop-val)
            (resolve-properties
              (into {}
                (map
                  (fn [[sub-key sub-val]]
                    [(keyword (str (name prop-key) "-" (name sub-key))) sub-val])
                  prop-val))))
          [[prop-key (str prop-val)]]))
      props)))

(defn- drop-chars [s n]
  (subs s 0 (- (count s) n)))

(defn- tag-selector-key? [k]
  (and
    (keyword? k)
    (let [k-name (name k)]
      (and
        (not (str/ends-with? k-name ">>"))
        (str/ends-with? k-name ">")))))

(defn- predicate-selector-key? [k]
  (and
    (keyword? k)
    (-> k name (str/ends-with? "?"))))

(defn- classes-selector-key? [k]
  (string? k))

(defn- pseudo-el-selector-key? [k]
  (and
    (keyword? k)
    (-> k name (str/ends-with? ">>"))))

(defn- prop-key? [k]
  (and
    (keyword? k)
    (not
      (or
        (tag-selector-key? k)
        (predicate-selector-key? k)
        (classes-selector-key? k)
        (pseudo-el-selector-key? k)))))

(defn- select-style-props [style]
  (into {}
    (keep
      (fn [[k v :as kv]]
        (when (prop-key? k)
          kv))
      style)))

(defn- select-style-nested [style]
  (into {}
    (keep
      (fn [[k v :as kv]]
        (when-not (prop-key? k)
          kv))
      style)))

(defn- update-last [v f & args]
  (let [new-v (apply f (last v) args)]
    (-> v pop (conj new-v))))

(defn- resolve-selector-next [resolved-path next-selector-key]
  (cond
    (tag-selector-key? next-selector-key)
    (conj resolved-path {:tag (-> next-selector-key name (drop-chars 1))})

    (pseudo-el-selector-key? next-selector-key)
    (do
      (when (empty? resolved-path)
        (panic "unnested pseudo-element selector" {:key next-selector-key}))
      (let [pseudo-el-name (-> next-selector-key name (drop-chars 2))]
        (update-last resolved-path
          (fn [last-selector]
            (when-let [parent-pseudo-el-name (:pseudo-el last-selector)]
              (panic "nested pseudo-selectors" {:child pseudo-el-name :parent parent-pseudo-el-name}))
            (assoc last-selector :pseudo-el pseudo-el-name)))))

    (classes-selector-key? next-selector-key)
    (let [classes (-> next-selector-key (str/split #"\s+") vec)]
      (conj resolved-path {:classes classes}))

    (predicate-selector-key? next-selector-key)
    (let [predicate (get-in *context* [:predicates next-selector-key])]
      (when (and (:selector predicate) (empty? resolved-path))
        (panic "selector predicate not nested" {:key next-selector-key}))
      (if (empty? resolved-path)
        [{:predicates #{predicate}}]
        (update-last resolved-path update :predicates (fnil conj #{}) predicate)))
    
    (vector? next-selector-key)
    (reduce
      (fn [m inner-next-selector-key]
        (resolve-selector-next m inner-next-selector-key))
      resolved-path
      next-selector-key)))

(defn- resolve-selectors-inner [resolved-path style]
  (into [[resolved-path (select-style-props style)]]
    (mapcat
      (fn [[k v]]
        (resolve-selectors-inner (resolve-selector-next resolved-path k) v))
      (select-style-nested style))))

(defn- resolve-selectors [style]
  (resolve-selectors-inner [] style))

(defn- resolved-path->queries [resolved-path]
  (->> resolved-path
    (mapcat :predicates)
    (keep :query)
    set))

(defn- indentation [n]
  (str/join (repeat (* 2 n) " ")))

(defn- render-css-selectors [resolved-path]
  (let [selectors
        (->> resolved-path
          (map
            (fn [{:keys [tag classes predicates pseudo-el]}]
              (str
                tag
                (when (seq classes)
                  (str "." (str/join "." classes)))
                (str/join (keep :selector predicates))
                (when pseudo-el
                  (str "::" pseudo-el)))))
          (str/join " "))]
    (if (str/blank? selectors)
      "*"
      selectors)))

(defn- render-css-props [level props]
  (->> props
    resolve-properties
    (map
      (fn [[k v]]
        (str (indentation level) (name k) ": " v ";\n")))
    str/join))

(defn- render-css-rule [level resolved-path props]
  (str
    (indentation level) (render-css-selectors resolved-path) " {\n"
    (render-css-props (inc level) props)
    (indentation level) "}\n"))

(defn- wrapped-in-queries [level queries styles]
  (cond
    (seq queries)
    (str
      (indentation level) (first queries) " {\n"
      (wrapped-in-queries (inc level) (rest queries) styles)
      (indentation level) "}\n")
    
    :else
    (->> styles
      (map
        (fn [[resolved-path props]]
          (render-css-rule level resolved-path props)))
      (str/join))))

(defn- exec-predicates [resolved-path]
  (->> resolved-path
    (mapcat :predicates)
    (keep :exec)
    (reduce
      (fn [acc exec-fn]
        (and acc (exec-fn)))
      true)))

(defn render-style [style {:keys [property-handlers predicates]}]
  (binding [*context*
            {:property-handlers (or property-handlers default-property-handlers)
             :predicates (or predicates default-predicates)
             :rendering :style}]
    (->> style
      resolve-selectors
      (filter #(and (seq (second %)) (exec-predicates (first %))))
      (group-by (comp resolved-path->queries first))
      (sort-by #(count (first %))) ; rules with queries come after to ensure correct precedence
      (map #(wrapped-in-queries 0 (seq (first %)) (second %)))
      (str/join "\n"))))

(defn render-font [font-spec {:keys [property-handlers]}]
  (binding [*context*
            {:property-handlers (or property-handlers default-property-handlers)
             :predicates {}
             :rendering :font}]
    (->> font-spec
      (map
        (fn [font-props]
          (str
            "@font-face {\n"
            (render-css-props 1 font-props)
            "}\n")))
      str/join)))

(defn render-animation [animation-name animation-frames]
  (str
    "@keyframes " animation-name " {\n"
    (->> animation-frames
      (map
        (fn [[k props]]
          (str
            (indentation 1)
            (cond
              (number? k)
              (str (* 100 k) "%")
              
              (keyword? k)
              (name k)
              
              :else
              k)
            " {\n"
            (render-css-props 2 props)
            (indentation 1) "}\n")))
      str/join)
    "}\n"))