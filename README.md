[![Clojars Project](https://img.shields.io/clojars/v/io.fkcss/fkcss.svg)](https://clojars.org/io.fkcss/fkcss)
> [!WARNING]
> This project has been archived and will no longer be maintained.  There are many alternatives
> on the market, including:
> - [noprompt/garden](https://github.com/noprompt/garden)
> - [green-coder/girouette](https://github.com/green-coder/girouette)


# FkCSS
Powerful styling without leaving Clojure/ClojureScript - f**k CSS.
FkCSS is a minimal `CLJ->CSS` library without the weight of `CLJSS`
and other alternatives.

## Features
- Styles scoped by Clojure namespace
- Fonts and animations via `@font-face` and `@keyframes`
- Concise syntax, but more expressive property values where desired
- Auto-prefixing
- Custom property handlers
- Only a few hundred lines of code

## Usage
Most useful things are defined in `fkcss.core`, so require that in
your module.
```clj
(ns ...
  (:require
    [fkcss.core :as ss]))
```

Styles are represented by maps of properties and nested style maps.The key determines how FkCSS interprets a value in the style map:
- Keywords ending in `>` denote a nested tag style
- Keywords ending in `>>` denote a nested pseudo-element style
- Keywords ending in `?` denote conditional properties
- Strings denote some number of whitespace delimited classes

Here's an example:
```clj
{:div>
 {:hovered?
  {:color "red"}

  :before>>
  {:color "blue"}

  "foo bar"
  {:color "pink"}}}
```
Which yields:
```css
div:hover {
  color: red;
}

div::before {
  color: blue;
}

div.foo.bar {
  color: pink;
}
```

Use a vector for more concise nesting.
```clj
{[:div> :before>>]
 {:color "blue"}}
```
Use a map for more concise sub-properties.
```clj
{:div>
 {:margin {:left "1rem" :right "1rem"}}}
```
Yields:
```css
div {
  margin-left: 1rem;
  margin-right: 1rem;
}
```

### `defclass`
Use `defclass` to define namespace scoped classes, it'll bind the
given var name to the name of the generated class.
```clj
(ss/defclass my-class
  {:color "red"

   :hovered?
   {:color "blue"}})

(defn my-component []
  [:div {:class my-class}
    "Hello"])
```

Properties at the root of a `defclass` apply to elements with the
defined class.  Properties in a nested node within a `defclass`
apply to elements within an element with the defined class.

### `defanimation`, `reg-animation!`
Namespace scoped animations can be defined with `defanimation`, or
animations with custom names can be registered with `reg-animation!`.
```clj
(ns example-ns)

(ss/defanimation example-1
 {:from {:opacity 0}
  :to {:opacity 1}})

(ss/reg-animation! "example-2"
 {0 {:opacity 0}
  1 {:opacity 1}})

(ss/reg-animation "example-3"
 {"0%" {:opacity 0}
  "100%" {:opacity 1}})
```
This yields.
```css
@keyframes example-ns-example-1 {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes example-2 {
  0% { opacity: 0; }
  100% { opacity: 1; }
}

@keyframes example-3 {
  0% { opacity: 0; }
  100% { opacity: 1; }
}
```
Nested nodes aren't allowed in animation property maps.

### `reg-font!`
Add fonts to the generated CSS with `reg-font!`.
```clj
(ss/reg-font! "Tangerine"
  [{:src "url(angerine-Regular.ttf) format('opentype')"
    :font-weight 400
    :font-style "normal"}
   {:src "url(Tangerine-Bold.ttf) format('opentype')"
    :font-weight 700
    :font-style "normal"}])
```
This yields.
```css
@font-face {
  src: url(/fonts/Tangerine-Regular.ttf) format('opentype');
  font-weight: 400;
  font-style: normal;
  font-family: 'Tangerine';
}
@font-face {
  src: url(/fonts/Tangerine-Bold.ttf) format('opentype');
  font-weight: 700;
  font-style: normal;
  font-family: 'Tangerine';
}
```
A single map can be given instead of the vector when only
one `@font-face` is needed.

### `reg-style!`
Use `reg-style!` to register global styles.  Properties at the
root of such style maps apply to all elements.  `reg-style!`
requires a key in addition to the style map itself so it can do
the right replacement/cleanup when namespaces are reloaded.
```clj
(ss/reg-style! ::global
 {:a>
  {:color "blue"
   :text-decoration "none"}})
```

### `gen-css`
Use `gen-css` to generate CSS for all registered styles.
```clj
(def css (ss/gen-css))
```

### `fkcss.cljs/mount!`
FkCSS can generate the CSS and add it to a `style` tag in
the DOM in one go, if running in a browser.
```clj
(ns ...
  (:require
    [fkcss.cljs :as ss-cljs]))

(ss-cljs/mount!)
```
Use `unmount!` to remove it.

## Property Handlers
Property handlers allow for custom translations from
FkCSS properties to CSS properties.  FkCSS comes with
some builtin handlers in `fkcss.render/default-property-handlers` which handle vendor prefixing and allow for some conveniences like `margin-x/margin-y` properties.  Custom handlers
can be passed into `gen-css`, but be sure to merge
them with the defaults if you want to keep the bultin ones.
```clj
(ss/gen-css
 {:property-handlers
  (merge
   fkcss.render/default-property-handlers
   {...custom handlers...})})
```

The map of property handlers should look like this:
```clj
{:property-name
 (fn [property-value]
  {:props
   {:property-name property-value
    :-webkit-property-name property-value
    :-ms-property-name property-value}})}
```
Where the `:props` map in the handlers result gives the
final CSS properties.

### Built-in Property Handlers
- `margin-x/margin-y` shorthand
- `padding-x/padding-y` shorthand
- `border-<edge>-radius` shorthand (`top/right/bottom/left`)
- `box-shadow` map value with explicit keys `#{:offset-x :offset-y :inset? :blur-radius :spread-radius}`
- Vendor prefixes for appropriate properties

Example of more expressive box shadow syntax.
```clj
{:box-shadow {:inset? true :offset-x 0 :offset-y 2}}
```


## Predicates
Predicates allow for conditional rules without depending on how the test is implemented.  Predicates are keys ending in `?` within a style map.  FkCSS has builtin predicates for the most
common cases, but custom predicates can also be given in `gen-css`.
```clj
(ss/gen-css
 {:predicates
  (merge
   fkcss.render/default-predicates
   {...custom predicates...})})
```

The predicates map should look like:
```clj
{:predicate-key?
 {:selector <css-selector>
  :exec <boolean-function>
  :query <css-query>}}
```
Any predicate field can be omitted, in which case it simply won't apply.

The `:selector` field should give a CSS selector to limit where the conditional rules will apply.  For example `:hover` or `.selected`.

The `:exec` field should give a function to be executed when
the CSS is being generated; if the function returns `false`
then the conditional CSS simply won't be generated.

The `:query` field should give a `@media` or `@supports` query
to predicate the rule on.

### Built-in Predicates
For CLJ and CLJS:
`:hovered?`, `:active?` `:focused?`, `:focus-visible?`,
`:enabled?`, `:disabled?`, `:visited?`, `:checked?`,
`:expanded?`, `:current?`, `:screen-tiny?`, `:screen-small?`,
`:screen-large?`, `:screen-huge?`, `:pointer-fine?`,
`:pointer-coarse?`, `:pointer-none?`, `:hoverable?`

For CLJS only: `:touchable?`

See `fkcss.render/default-predicates` for how these or implemented
and as examples for custom predicates.
