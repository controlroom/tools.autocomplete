(ns ctrlrm.autocomplete
  (:require [wire.core :as w]
            [wire.up.show :as wired :include-macros true]
            [show.core :as show :include-macros true]
            [show.dom  :as dom  :include-macros true]))

(show/defclass DefaultInput [component]
  (render [{:keys [local-wire value]} _]
    (wired/input local-wire
                 {:type         "text"
                  :autoComplete "off"
                  :spellCheck   "false"
                  :value        value})))

(show/defclass DefaultItem [component]
  (render [{:keys [item]} _]
    (dom/p item)))

(show/defclass DefaultResults [component]
  (render [{:keys [items item-component value highlight-index active
                   local-wire]}
           state]
    (dom/ul {:className (show/class-map {"empty" (or (not active) (empty? items))})}
      (map-indexed
        (fn [idx item]
          (wired/li (w/lay local-wire nil {:item item})
                    {:className (show/class-map {"selected" (= idx highlight-index)})}
                    (item-component (merge {:value value}
                                           (if (map? item) item {:item item})))))
        items))))

(defn autocomplete-fn [text result-fn]
  (result-fn [(str text ", really?") "implement" "your" "own" "autocomplete-fn"]))

(defn recieve-results [component results]
  (doto component
    (show/assoc! :results results)
    (show/assoc! :loading false)))

(defn has-new-value [component value]
  (let [results-fn (show/get-props component :results-fn)]
    (doto component
      (show/assoc! :value value)
      (show/assoc! :loading true))
    (results-fn value (partial recieve-results component))) )

(defn has-empty-value [component]
  (doto component
    (show/assoc! :value "")
    (show/assoc! :results [])))

(defn input-change [component value]
  (show/assoc! component :highlight-index 0)
  (if (= value "")
    (has-empty-value component)
    (has-new-value component value)))

(defn hover-change [component fn]
  (show/update! component :highlight-index fn))

(defn hide [component]
  (show/assoc! component :active false))

(defn show [component]
  (show/assoc! component :active true))

(defn selection [component item]
  (show/assoc! component :results [])
  (show/assoc! component :value
               ((show/get-props component :result-selection-fn) item)))

(defn enter-key [component]
  (let [{:keys [results highlight-index]} (show/get-state component)]
    (if (not (empty? results))
      (selection component (nth results highlight-index)))))

(defn input-wire [component]
  (w/taps (w/wire)
    {:action :focus} #(show component)
    {:action :blur} #(hide component)
    {:action :change} #(input-change component (:value %))
    {:action :up :keypress :enter} #(enter-key component)
    {:action :up :keypress :up-arrow} #(hover-change component dec)
    {:action :up :keypress :down-arrow} #(hover-change component inc)))

(defn results-wire [component]
  (w/taps (w/wire)
    {:event :mouse-click} #(selection component (:item %))))

(show/defclass Autocomplete
  "Can do some autocomplete here son"
  [component]
  (default-props []
    {:wire                (w/wire)
     :input-component     DefaultInput
     :results-component   DefaultResults
     :item-component      DefaultItem
     :results-fn          autocomplete-fn
     :result-selection-fn identity
     :parent-class-name   "autocomplete"})
  (initial-state []
    {:value      (or (show/get-props component :value) "")
     :results    []
     :loading    false
     :highlight-index 0
     :active     false})
  (render [{:as params
            :keys [parent-class-name input-component results-component
                   item-component]}
           {:as state
            :keys [value results highlight-index active]}]
    (dom/div {:key "parent" :className parent-class-name}
      (input-component
        {:local-wire (input-wire component)
         :key "input"
         :value value})
      (results-component
        {:local-wire (results-wire component)
         :key "results"
         :items results
         :value value
         :highlight-index highlight-index
         :item-component item-component
         :active active}))))
