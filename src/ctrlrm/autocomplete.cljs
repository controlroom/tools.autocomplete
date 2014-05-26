(ns ctrlrm.autocomplete
  (:require [wire.core :as w]
            [wire.up.show :as wired :include-macros true]
            [show.core :as show :include-macros true]
            [show.dom  :as dom  :include-macros true]))

(defn input-key-down [component key-event]
  (case (.-keyCode key-event)
    40 ((show/get-props component :handle-hover-change) inc)
    38 ((show/get-props component :handle-hover-change) dec)
    13 ((show/get-props component :handle-enter-key))
    nil))

(show/defclass DefaultInput [component]
  (render [{:keys [value handle-change handle-blur handle-focus]} _]
    (dom/input {:type         "text"
                :autoComplete "off"
                :spellCheck   "false"
                :value        value
                :onBlur       #(js/setTimeout
                                 (fn [] (handle-blur component))
                                 100)
                :onFocus      #(handle-focus component)
                :onKeyDown    #(input-key-down component %)
                :onChange     #(handle-change (.. % -target -value))})))

(show/defclass DefaultItem [component]
  (render [{:keys [item]} _]
    (dom/p item)))

(show/defclass DefaultResults [component]
  (render [{:keys [items item-component value highlight-index active
                   handle-selected-item]}
           state]
    (dom/ul {:className (show/class-map {"empty" (or (not active) (empty? items))})}
      (map-indexed
        (fn [idx item]
          (dom/li {:className (show/class-map {"selected" (= idx highlight-index)})
                   :onClick #(handle-selected-item item)}
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

(show/defclass Autocomplete
  "Can do some autocomplete here son"
  [component]
  (default-props []
    {:input-component     DefaultInput
     :results-component   DefaultResults
     :item-component      DefaultItem
     :results-fn          autocomplete-fn
     :result-selection-fn identity
     :parent-class-name   "autocomplete"})

  (initial-state []
    {:value     (or (show/get-props component :value) "")
     :results   []
     :loading   false
     :highlight-index 0
     :active    false})

  (render [{:keys [parent-class-name input-component results-component
                   item-component] :as params}
           {:keys [value results highlight-index active] :as state}]
    (dom/div {:key "parent" :className parent-class-name}
      (input-component
        {:key "input"
         :value value
         :handle-blur (partial hide component)
         :handle-focus (partial show component)
         :handle-selected-item (partial selection component)
         :handle-hover-change (partial hover-change component)
         :handle-enter-key (partial enter-key component)
         :handle-change (partial input-change component)})
      (results-component
        {:key "results"
         :items results
         :value value
         :highlight-index highlight-index
         :item-component item-component
         :active active
         :handle-selected-item (partial selection component)}))))
