# FkCSS
Powerful styling without leaving Clojure/ClojureScript - f**k CSS.

:warning: This project is in an early stage of development.
:zzz: Docs coming soon.

## Class Defs
Clojure:
```clj
(defclass example-1
  {:margin-top "1rem"
   :margin-bottom "2rem"})
```
CSS:
```css
.example-1-12805 {
  margin-top: 1rem;
  margin-bottom: 2rem;
}
```

## Map Comperhension
Clojure:
```clj
(defclass example-2
  {:margin {:top "1rem" :bottom "2rem"}})
```
CSS:
```css
.example-2-12922 {
  margin-top: 1rem;
  margin-bottom: 2rem;
}
```

## Tests
Clojure:
```clj
(defclass example-3
  {:background-color "white"

   :test/screen-small?
   {:font-size "16pt"}
   
   :test/hovered?
   {:background-color "black"}})
```
CSS:
```css
.example-3-8156 {
  background-color: white;
}
@media (min-width: 640px) and (max-width: 767px) {
  .example-3-8156 {
    font-size: 16pt;
  }
}
.example-3-8156:hover {
  background-color: black;
}
```

## Nesting
Clojure:
```clj
(defclass example-4
  {:tag/a
   {:text-decoration "none"
    :color "blue"}
   
   :test/hovered?
   {:class/highlight-on-container-hover
    {:background-color "yellow"}}})
```
CSS:
```css
.example-4-7687 a {
  text-decoration: none;
  color: blue;
}
.example-4-7687:hover .highlight-on-container-hover {
  background-color: yellow;
}
```

## Deep Nesting
Clojure:
```clj
(defclass example-5
  {[:class/some-thang :tag/p :pseudo/selection]
  {:color "red"}})
```
CSS:
```css
.example-5-8293 .some-thang p::selection {
  color: red;
}
```

## Inline Animations
Clojure:
```clj
(defclass example-6
  {:animation
   {:duration "300ms"
    :timing-function "linear"
    :frames {:from {:left 0} :to {:left 100}}}})
```
CSS:
```css
.example-6-8410 {
  animation-duration: 300ms;
  animation-timing-function: linear;
  animation-name: fkcss-animation-8412;
}
@keyframes fkcss-animation-8412 {
  from {
    left: 0;
  }
  to {
    left: 100;
  }
}
```

## CSS Output
```clj
(def css (gen-css))
```

## CSS DOM Injection
```clj
(fkcss.cljs/mount!)
```