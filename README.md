[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.raystubbs/fkcss.svg)](https://clojars.org/org.clojars.raystubbs/fkcss)

# FkCSS
Powerful styling without leaving Clojure/ClojureScript - f**k CSS.

## The Gist
Require FkCSS, make sure to add `:include-macros true` for ClojureScript.
```clj
(ns example.core
  (:require
    [fkcss.core :as ss :include-macros true]))
```

Define classes with `defclass`.
```clj
(ss/defclass button-style
  {:padding {:left "1rem" :right "1rem" :top "0.5rem" :bottom "0.5rem"}
   :background {:color "rgb(189, 193, 199)"}
   :border {:width "1px" :style "solid" :color "rgb(119, 120, 122)"}
   :color "rgb(119, 120, 122)"
   
   :test/hovered?
   {:background-color "rgb(237, 238, 240)"}
   
   :test/screen-small?
   {:font-size "16pt"}}})
```

Attach it to a component.
```clj
(defn button [{:keys [text on-click]}]
  [:button
   {:class button-style :on-click on-click}
  text])
```

Get the CSS.
```clj
(defn app []
  [:div
    [:style (ss/gen-css)]
    ...])
```

As an alternative to manually injecting the generated CSS, in ClojureScript you
can do.
```clj
(fkcss.cljs/mount!) ; add CSS to the DOM
(fkcss.cljs/unmount!) ; remove it when finished, unnecessary for most apps
```

## Tests
Tests are a concise and expressive way of using conditional styling,
regardless of whether those conditions are implemented as media queries,
selectors, or something else.  Instead of saying something like this
(from CLJSS readme):
```clj
::css/media
{[:only :screen :and [:max-width "460px"]]
 {:height (/ height 2)}}
```
In FkCSS we say:
```clj
:test/screen-small?
{:height (/ height 2)}
```

The tests available in FkCSS by default are in `fkcss.core/DEFAULT-QUERY-TESTS`
and `fkcss.core/DEFAULT-SELECTOR-TESTS`.  Here's what they look like:
```clj
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
```
Query tests consist of the query to be applied (can be media or feature query) and
a priority (determining the order their CSS is rendered in relative to each other).  Selector tests consist of a single selector to be added to the current style
node's base selector.

Tests are fully configurable via the config map passed to `gen-css`:
```clj
(ss/gen-css
 {:query-tests
  (merge
   ss/DEFAULT-QUERY-TESTS
   {:screen-massive? {:query "@media (min-width: 2560px)"}})
   
   :selector-tests
   (merge
    ss/DEFAULT-SELECTOR-TESTS
    {:current? ".current"})})
```

## Nested Styling
FkCSS allows nested things to be styled via the `test`, `tag`, `class`,
and `pseudo` keyword namespaces.
```clj
:class/scrollbar-thin
 {:pseudo/-webkit-scrollbar
  {:width "0.5rem"
   :height "0.5rem"}}
```
More concisely, multiple levels of nesting can be expressed as a vector:
```clj
[:class/scrollbar-thin :pseudo/-webkit-scrollbar]
{:width "0.5rem"
 :height "0.5rem"}
```

## Inline Animations
FkCSS doesn't support custom `@keyframe` animations, since we aren't trying
to recreate CSS in Clojure.  We wanna make something more concise and expressive,
so FkCSS has inline animations.
```clj
(defclass some-drawer-opening
 {:animation
  {:duration "300ms"
   :timing-function "linear"
   
   :frames
   {:from {:top -500}
    :to {:top 0}}}})

(defclass some-drawer-closing
 {:animation
  {:duration "300ms"
   :timing-function "linear"
   
   :frames
   {:from {:top 0}
    :to {:top -500}}}})
```

As a last resort, or if you just don't like the idea of inline animations, custom
keyframe animations can be defined in plain ol' CSS via `include-css` as described
later.

## Theming
FkCSS provides two facilities to help with theming: the `:root-selector` config and
functional properties.

The `root-selector` allows for an additional selector to be added
at the start of each CSS rule, for example `[data-theme="dark"]`, which can then be
added as an attribute to the document `body` (or another container) to make sure only
things within the container are styled by the generated CSS.

Functional properties are functions appearing in the place of a property value,
which take a `theme` (given via the `:theme` config) and produce a style map.

Here's how these two features work together to allow for dynamic theming.
```clj
(ss/defclass button-style
 {:background-color (fn [theme] (case theme :dark "rgb(88,88,88)" :light "white"))})

 ; or use plain `gen-css`, but mount! is easier for front-end styling
 (ss/mount! "light-theme" {:root-selector "[data-theme=\"light\"]" :theme :light})
 (ss/mount! "dark-theme" {:root-selector "[data-theme=\"dark\"]" :theme :dark})
 ```

## Raw CSS
Though FkCSS tries to cover most styling needs without requiring raw CSS, there
will always be edge cases, so you can import an external style sheet with
`import-css` or add a raw chunk of CSS to what's produced via `include-css`.
Both of these require a key so FkCSS can handle hot reloads properly.

```clj
(ss/import-css :some-external-css "/some-external-style-sheet.css")
(ss/include-css :some-custom-css "a { text-decoration: none; }")
```

## Class Names
By default `defclass` produces unique class names based on the given def name,
this is usually what you'll want since it avoids name collisions and ensures
a unique scope for nested styles.  There may be some cases though when you'd
like to refer to FkCSS classes in an external environment or otherwise without
access to the Clojure def.  FkCSS has two options to make this work: `^:exact`
and `reg-class`.

Adding the `^:exact` metadata tag to any `defclass` will ensure that the CSS
class produced has the same name as the def, so the following would produce a
CSS class named `some-class`.
```clj
(ss/defclass some-class
  ...)
```

You can also manually register a style with a custom class name with `reg-class`,
this is more verbose as FkCSS requires you to give the registration a key.
```clj
(ss/reg-class :some-class "some-class"
  ...)
```

## Format Utility
Any dynamic style generation is gonna rely on templating and other means of string
generation quite heavily.  To make such code more expressive than it might be
with `clojure.core/format`, FkCSS provides a name base templating utility `fkcss.core/format`,
though it doesn't have any of the nice number formatting capabilities of the former.
```clj
(ss/format "rgb({red}, {green}, {blue})" {:red 123 :green 123 :blue 123})
