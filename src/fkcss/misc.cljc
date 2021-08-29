(ns fkcss.misc
  (:require
    [clojure.string :as str]))

(defn panic [msg kvs]
  (throw
    #?(:clj (ex-info msg kvs)
       :cljs (js/Error. msg #js{:cause (str kvs)}))))

(defn reduce-whitespace [s]
  (-> s (str/replace #"\s+" " ") str/trim))