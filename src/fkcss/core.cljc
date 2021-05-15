(ns fkcss.core
  (:require
   [clojure.string :as str]
   [clojure.set :as set]))

(def ^:private MEDIA-QUERY-PREDICATE->CONDITION
  {:screen-tiny? "(max-width: 639px)"
   :screen-small? "(min-width: 640px) and (max-width: 767px)"
   :screen-medium? "(min-width: 768px) and (max-width: 1023px)"
   :screen-large? "(min-width: 1024px) and (max-width: 1279px)"
   :screen-huge? "(min-width: 1280px)"
   :pointer-fine? "(pointer: fine)"
   :pointer-coarse? "(pointer: coarse)"
   :pointer-none? "(pointer: none)"
   :pointer-hoverable? "(hover: hover)"})

(def ^:private PSEUDO-CLASS-PREDICATE->PSEUDO-CLASS
  {:hovered? ":hover"
   :active? ":active"
   :focused? ":focus"
   :enabled? ":enabled"
   :disabled? ":disabled"
   :visited? ":visited"
   :checked? ":checked"})

(def ^:private MEDIA-QUERY-PREDICATES
  (set (keys MEDIA-QUERY-PREDICATE->CONDITION)))

(def ^:private PSEUDO-CLASS-PREDICATES
  (set (keys PSEUDO-CLASS-PREDICATE->PSEUDO-CLASS)))

(def ^:private PREDICATE-PROPS
  (set/union
   MEDIA-QUERY-PREDICATES
   PSEUDO-CLASS-PREDICATES))

(def ^:const INIT-CONTEXT {:style->class-name {}})
(def ^:dynamic *context* (atom INIT-CONTEXT))
(def ^:dynamic ^:private *opts* {})

(defn- panic [& msg]
  (throw (#?(:clj RuntimeException. :cljs js/Error.) (str/join msg))))

(defn- spy
  ([v]
   (prn v)
   v)
  ([label v]
   (print label ": " (pr-str v))
   v))

(defn class-for [style]
  (swap! *context* update-in [:style->class-name style] #(or % (name (gensym "cljss-"))))
  (get @*context* style))

(defn- media-query-vector? [v]
  (and
   (vector? v)
   (= :media (nth v 0))
   (or
    (string? (nth v 1))
    (panic "invalid media query vector: " v))))

(defn- extract-predicate-styles [style]
  (reduce-kv
   (fn [[r p] k v]
     (cond
       (or (contains? PREDICATE-PROPS k) (string? k) (media-query-vector? k))
       [r
        (let [[root-style predicate-set->style] (extract-predicate-styles v)]
          (->
           (reduce-kv
            (fn [p predicate-set predicate-style]
              (update p (conj predicate-set k) merge predicate-style))
            p
            predicate-set->style)
           (assoc #{k} root-style)))]

       :else
       [(assoc r k v) p]))
   [{} {}]
   style))


; See: http://shouldiprefix.com/
(defn- with-vendor-translations [[k v]]
  (case k
    "background-clip"
    (cond-> []
      (= "text" v)
      (conj ["-webkit-background-clip" v])

      :always
      (conj ["background-clip" v]))

    "box-reflect"
    [["-webkit-box-reflect" v]
     ["box-reflect" v]]

    "filter"
    [["-webkit-filter" v]
     ["filter" v]]

    "display"
    (case v
      "flex"
      [["display" "-webkit-box"]
       ["display" "-ms-flexbox"]
       ["display" "-webkit-flexbox"]
       ["display" "flex"]]

      "grid"
      [["display" "-ms-grid"]
       ["display" "grid"]]

      #_default
      [["display" v]])

    "flex"
    [["-webkit-box-flex" v]
     ["width" "20%"]  ; for old syntax, otherwise collapses (from shouldiprefix.com#flexbox)
     ["-webkit-flex" v]
     ["-ms-flex" v]
     ["flex" v]]

    "font-feature-settings"
    [["-webkit-font-feature-settings" v]
     ["-moz-font-feature-settings" v]
     ["font-feature-settings" v]]

    "hyphens"
    [["-webkit-hyphens" v]
     ["-moz-hyphens" v]
     ["-ms-hyphens" v]
     ["hyphens" v]]

    "word-break"
    [["ms-word-break" v]
     ["word-break" v]]

    "mask-image"
    [["-webkit-mask-image" v]
     ["mask-image" v]]

    "column-count"
    [["-webkit-column-count" v]
     ["-moz-column-count" v]
     ["column-count" v]]

    "column-gap"
    [["-webkit-column-gap" v]
     ["-moz-column-gap" v]
     ["column-gap" v]]

    "column-rule"
    [["-webkit-column-rule" v]
     ["-moz-column-rule" v]
     ["column-rule" v]]

    "object-fit"
    [["-o-object-fit" v]
     ["object-fit" v]]

    "transform"
    [["-webkit-transform" v]
     ["-ms-transform" v]
     ["transform" v]]

    "appearance"
    [["-webkit-appearance" v]
     ["-moz-appearance" v]
     ["appearance" v]]

    #_default
    [[k v]]))

(defn- clj->css [v]
  (cond
    (map? v)
    nil

    (string? v)
    v

    (number? v)
    (str v)

    (or (keyword? v) (symbol? v))
    (str v)

    (seqable? v)
    (cond
      ; if there are any groupings then all things are separated by commas
      (some #(and (not (string? %)) (not (map? %)) (seqable? %)) v)
      (str/join ", " (map clj->css v))

      :else
      (str/join " " (map clj->css v)))

    (fn? v)
    (let [r (v (:theme *opts*))]
      (if (fn? r)
        (panic "style generator function returns another function, that's not allowed")
        (clj->css r)))))

(defn- style->css-props [style]
  (mapcat
   (fn [[k v]]
     (let [maybe-css-val (clj->css v)]
       (cond
         (some? maybe-css-val)
         [[(name k) maybe-css-val]]

         (map? v)
         (map
          (fn [[inner-css-k inner-css-v]]
            [(str (name k) "-" inner-css-k) inner-css-v])
          (style->css-props v))

         :else
         (panic "invalid value for " k ":" (prn-str v)))))
   style))

(defn pseudo-el-key? [k]
  (and
   (keyword? k)
   (str/starts-with? (name k) ">")))

(defn- gen-predicate-set-rules [class-name predicate-set style]
  (let [{mq-predicates :media-query
         pc-predicates :pseudo-class
         mqv-predicates :media-query-vector
         raw-predicates :raw}
        (group-by
         (fn [predicate]
           (cond
             (contains? MEDIA-QUERY-PREDICATES predicate)
             :media-query

             (contains? PSEUDO-CLASS-PREDICATES predicate)
             :pseudo-class

             (media-query-vector? predicate)
             :media-query-vector

             :else
             :raw))
         predicate-set)

        selector-indent (when (seq mq-predicates) "  ")
        inner-indent (if (seq mq-predicates) "    " "  ")

        base-selector
        (str
         selector-indent
         (when-let [selector (:selector *opts*)]
           (str selector " "))
         "." class-name
         (str/join
          (concat
           (map PSEUDO-CLASS-PREDICATE->PSEUDO-CLASS pc-predicates)
           raw-predicates)))

        style->css
        (fn [style pseudo-el]
          (str
           base-selector
           (when pseudo-el
             (str "::" (subs (name pseudo-el) 1)))

           " {\n"

           (->> style
                (filter (comp not pseudo-el-key? key))
                style->css-props
                (mapcat with-vendor-translations)
                (map #(str inner-indent (nth % 0) ": " (nth % 1) ";\n"))
                (str/join))

           selector-indent "}\n"))

        pseudo-el-styles
        (keep
         (fn [[k :as kv]]
           (when (pseudo-el-key? k)
             kv))
         style)]
    (str
     (when (seq mq-predicates)
       (str
        "@media "
        (str/join
         " and "
         (concat
          (map MEDIA-QUERY-PREDICATE->CONDITION mq-predicates)
          (map second mqv-predicates)))
        " {\n"))

     (style->css style nil)
     (str/join
      (map
       (fn [[k v]]
         (style->css v k))
       pseudo-el-styles))


     (when (seq mq-predicates)
       "}"))))

(defn- gen-class-rules [class-name style]
  (let [[root-style predicate-set->style] (extract-predicate-styles style)]
    (str/join
     "\n"
     (map
      (partial apply gen-predicate-set-rules class-name)
      (cons
       [#{} root-style]
       predicate-set->style)))))

(defn gen-css
  ([] (gen-css nil))
  ([opts]
   (binding [*opts* opts]
     (->> @*context*
          :style->class-name
          (map #(gen-class-rules (val %) (key %)))
          str/join))))

(defn rgb [r g b]
  (str "rgb(" r ", " g ", " b ")"))

(defn rgba [r g b a]
  (str "rgba(" r ", " g ", " b ", " a ")"))

(defn hsl [h s l]
  (str "hsl(" h ", " s ", " l ")"))

(defn hsla [h s l a]
  (str "hsla(" h ", " s ", " l ", " a ")"))

(defn url [s]
  (str "url(" s ")"))

(defn calc [& ss]
  (str "calc(" (str/join " " ss) ")"))

;; test
;;(def example-class
;;  (class-for
;;   {:color "white"
;;    :border {:color "black" :width "1px" :style :solid}
;;    :font-size "12pt"
;;
;;    :hovered?
;;    {:background {:color "blue"}}
;;
;;    :screen-small?
;;    {:font-size "16pt"}
;;
;;    :>before
;;    {:content "\"\""
;;     :position "absolute"
;;     :left 0
;;     :top 0
;;     :bottom 0
;;     :right 0
;;     :background {:color (rgb 25 25 25)}}}))
;;
;;(print (gen-css {:selector "[data-theme=\"dark\"]"}))
