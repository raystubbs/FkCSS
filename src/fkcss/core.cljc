(ns fkcss.core
  (:require
    [clojure.string :as str]))

(declare ^:private ^:dynamic *config*)

(defn- panic [& msg]
  (throw (#?(:clj IllegalArgumentException. :cljs js/Error.) (str/join msg))))

(def DEFAULT-QUERY-TESTS
  {:screen-tiny? {:query "@media (max-width: 639px)" :priority 1}
   :screen-small? {:query "@media (max-width: 767px)" :priority 2}
   :screen-large? {:query "@media (min-width: 1024px)" :priority 2}
   :screen-huge? {:query "@media (min-width: 1280px)" :priority 1}
   :pointer-fine? {:query "@media (pointer: fine)"}
   :pointer-coarse? {:query "@media (pointer: coarse)"}
   :pointer-none? {:query "@media (pointer: none)"}
   :pointer-hoverable? {:query "@media (hover: hover)"}})

(def DEFAULT-SELECTOR-TESTS
  {:hovered? ":hover"
   :active? ":active"
   :focused? ":focus"
   :visibly-focused? ":focus-visible"
   :enabled? ":enabled"
   :disabled? ":disabled"
   :visited? ":visited"
   :checked? ":checked"
   :expanded? "[aria-expanded=\"true\"]"
   :current? "[aria-current]"})

; See: http://shouldiprefix.com/
; I imagine there are things missing from here
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
    (let [r (v (:theme *config*))]
      (if (fn? r)
        (panic "style generator function returns another function, that's not allowed")
        (clj->css r)))))

(defn- clj-props->css-props [clj-props]
  (mapcat
    (fn [[k v]]
      (cond
        (map? v)
        (mapcat
          (fn [[inner-css-k inner-css-v]]
            (with-vendor-translations
              [(str (name k) "-" inner-css-k) inner-css-v]))
          (clj-props->css-props v))

        (nil? v)
        nil

        :else
        (with-vendor-translations
          [(name k) (clj->css v)])))
    clj-props))

(defn- path->selector [path]
  (loop [remaining-path path
         selector-parts []]
    (if (empty? remaining-path)
      (str/join selector-parts)
      (let [[node-key & remaining-path] remaining-path

            [test-keys remaining-path]
            (split-with #(= "test" (namespace %)) remaining-path)

            _
            (when (and (seq test-keys) (= (:parse-context *config*) :animation-frame))
              (panic "can't have tests in animation-frames, found `" test-keys "`"))

            base-selector
            (when node-key
              (case (namespace node-key)
                "class"
                (str " ." (name node-key))

                "root"
                (str "." (name node-key))

                "pseudo"
                (str "::" (name node-key))

                "tag"
                (str " " (name node-key))))

            {:keys [query-tests selector-tests]} *config*


            test-selectors
            (reduce
              (fn [m test-key]
                (let [k (-> test-key name keyword)
                      test-selector (get selector-tests k)]
                  (cond
                    (contains? query-tests k)
                    m

                    (some? test-selector)
                    (conj m test-selector)

                    :else
                    (panic "unknown test `" test-key "`"))))
              []
              test-keys)]
        (recur
          remaining-path
          (-> selector-parts
            (cond->
              base-selector
              (conj base-selector))
            (into test-selectors)))))))

(defn- wrap-with-query-tests [path indent body-fn]
  (let [{:keys [query-tests]} *config*
        indent-str (str/join (repeat indent "  "))

        remaining-path
        (drop-while
          (fn [path-key]
            (let [k (-> path-key name keyword)]
              (not (and (= "test" (namespace path-key)) (contains? query-tests k)))))
          path)

        [query-test-key & remaining-path] remaining-path]
    (cond
      (nil? query-test-key)
      (body-fn indent-str)

      :else
      (str
        indent-str (get-in query-tests [(-> query-test-key name keyword) :query]) " {\n"
        (wrap-with-query-tests remaining-path (inc indent) body-fn)
        indent-str "}\n"))))

(defn- clj-time-offset->css-time-offset [time-offset]
  (cond
    (number? time-offset)
    (cond
      (<= 0 time-offset 1)
      (str (int (* 100 time-offset)) "%")

      :else
      (panic "numeric time offsets must be between 0-1, found `" time-offset "`"))

    (or (string? time-offset) (keyword? time-offset))
    (name time-offset)

    :else
    (panic "can't understand time offset `" time-offset "`")))

(defn- style->css [path style]
  (let [; special handling for `animation-frames: ...` and `animation: {:frames ...}`,
        ; it gets translated into a @keyframe form and its name replaces `animation-name`.
        animation-frames (or (:animation-frames style) (-> style :animation :frames))
        animation-name (when animation-frames (gensym "fkcss-animation-"))

        style
        (cond-> style
          animation-name
          (->
            (dissoc :animation-frames)
            (update :animation assoc :name animation-name :frames nil)))

        {props :prop children :node}
        (group-by
          (fn [[k v]]
            (if (and (keyword? k) (nil? (namespace k)))
              :prop
              :node))
          style)

        selector (path->selector path)]
    (str
      (wrap-with-query-tests path 0
        (fn [indent-str]
          (str

            (when (seq props)
              (str
                indent-str selector " {\n"

                (->> props
                  clj-props->css-props
                  (map #(str indent-str "  " (nth % 0) ": " (nth % 1) ";\n"))
                  str/join)

                indent-str "}\n"))

            (when animation-name
              (binding [*config* (assoc *config* :parse-context :animation-frame)]
                (str
                  indent-str "@keyframes " animation-name " {\n"
                  (->> animation-frames
                    (map
                      (fn [[time-offset style]]
                        (str
                          indent-str "  " (clj-time-offset->css-time-offset time-offset) " {\n"
                          (->> style
                            clj-props->css-props
                            (map #(str indent-str "    " (nth % 0) ": " (nth % 1) ";\n"))
                            str/join)
                          indent-str "  }\n")))
                    str/join)
                  indent-str "}\n"))))))
      (->> children
        (sort-by
          (fn [[k v]]
            (when (= "test" (namespace k))
              (get-in *config* [:query-tests (-> k name keyword) :priority]))))
        (map
          (fn [[k v]]
            (style->css (if (vector? k) (into path k) (conj path k)) v)))
        str/join))))

(defn new-context []
  (atom {:class-reg {} :css-imports {} :css-includes {} :next-index 0}))

(defonce ^:dynamic *context* (new-context))

(defn- take-index []
  (let [index (:next-index @*context*)]
    (swap! *context* update :next-index inc)
    index))

(defn reg-class
  ([class-name style]
    (reg-class class-name class-name style))
  ([key class-name style]
    (if (-> @*context* :class-reg (contains? key))
      (swap! *context* update-in [:class-reg key] merge {:class-name class-name :style style})
      (swap! *context* assoc-in [:class-reg key] {:class class-name :style style :index (take-index)}))
    class-name))

(defn defclass* [name style exact?]
  (let [class-name
        (if exact?
          (clojure.core/name name)
          (or
            (get-in @*context* [:class-reg name :class])
            (str (gensym (str name "-")))))]
    (reg-class name class-name style)))

(defmacro defclass [name style]
  {:pre [(symbol? name) (map? style)]}
  `(def ~name (defclass* '~name ~style ~(-> name meta :exact))))

(defn import-css [key url]
  (if (-> @*context* :css-imports (contains? key))
    (swap! *context* update-in [:css-imports key] merge {:url url})
    (swap! *context* assoc-in [:css-imports key] {:url url :index (take-index)}))
  nil)

(defn include-css [key css]
  (if (-> @*context* :css-includes (contains? key))
    (swap! *context* update-in [:css-includes key] merge {:css css})
    (swap! *context* assoc-in [:css-includes key] {:css css :index (take-index)}))
  nil)
 
(defn gen-css
  ([]
    (gen-css {}))
  ([config]
    (binding [*config*
              (-> {:selector-tests DEFAULT-SELECTOR-TESTS :query-tests DEFAULT-QUERY-TESTS}
                (merge config)
                (assoc :parse-context :root))]
      (->>
        (concat
          (->> @*context*
            :css-imports
            vals
            (map
              (fn [{:keys [url index]}]
                [index (str "@import \"" url "\";\n")])))
          (->> @*context*
            :class-reg
            vals
            (map
              (fn [{:keys [class style index]}]
                [index (style->css [(keyword "root" class)] style)])))
          (->> @*context*
            :css-includes
            vals
            (map
              (fn [{:keys [css index]}]
                [index (str css "\n")]))))
        (sort-by first)
        (map second)
        str/join))))

(defn format [fmt args]
  (str/replace fmt #"\{((?:[^}]|\\\})+)\}"
    (fn [[_ key]]
      (if (= "\\}" key)
        "}"
        (or (get args (keyword key)) (get args key))))))