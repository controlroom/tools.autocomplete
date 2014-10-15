(ns ctrlrm.autocomplete
  (:require [wire.core    :as w]
            [wire.up.show :as wired :include-macros true]
            [show.core    :as show :include-macros true]
            [show.dom     :as dom  :include-macros true]))

(show/defclass DefaultInput [component]
  (render [{:keys [local-wire value]} _]
    (wired/input local-wire
                 {:type         "text"
                  :autoComplete "off"
                  :spellCheck   "false"
                  :value        value})))

(show/defclass DefaultHeader [component]
  (render [{:keys [title]} state]
    (dom/h3 title)))

(show/defclass DefaultItem [component]
  (render [{:keys [item]} _]
    (dom/p item)))

(show/defclass DefaultResults [component]
  (render [{:keys [items item-component value highlight-index active
                   local-wire results-mod-fn header-component]}
           state]
    (dom/ul {:className
             (show/class-map
               {"autocomplete-results" true
                "empty" (or (not active) (empty? items))})}
      (map
        (fn [[idx item]]
          (if idx
            (wired/li (w/lay local-wire nil {:item item})
                      {:key (or (:id item) idx)
                       :className (show/class-map {"selected" (= idx highlight-index)})}
                      (item-component (merge {:value value}
                                             (if (map? item) item {:item item}))))
            (dom/li {:className "heading"}
                    (header-component (if (map? item)
                                        item
                                        {:title item})))))
        (results-mod-fn items)))))

(defn autocomplete-fn [text result-fn]
  (result-fn [(str text ", really?") "implement" "your" "own" "autocomplete-fn"]))

(defn order-groups [results]
  (sort-by
    (fn [[k v]]
      (* -1 (:pos k)))
    results))

(defn receive-results
  "We can receive 2 types of results:
  A sequence which represents items
    or
  A map of which represents groups of items"
  [component results]
  (let [results (cond
                  (map? results) (loop [res (order-groups results)
                                        idx 0
                                        final []]
                                   (if-not (seq res)
                                     final
                                     (let [[group items] (first res)]
                                       (recur (rest res)
                                              (+ idx (count items))
                                              (apply concat final
                                                     [[nil group]]
                                                     [(mapv vector (drop idx (range)) items)])))))
                  :default (map vector (range) results))]
    (show/assoc! component
                 :results results
                 :loading false)))

(defn has-new-value [component value]
  (let [results-fn (show/get-props component :results-fn)]
    (show/assoc! component
                 :value value
                 :loading true)
    (results-fn value (partial receive-results component))) )

(defn input-change [component value]
  (show/assoc! component :highlight-index 0)
  (has-new-value component value))

(defn hover-change [component f]
  (show/update-in! component :highlight-index
                   (fn [current-index]
                     (min
                       (dec (count (show/get-state component :results)))
                       (max 0 (f current-index))))))

(defn hide [component]
  (show/assoc! component :active false))

(defn show [component]
  (show/assoc! component :active true))

(defn selection [component item]
  (w/act (show/get-props component :wire) :ac-selected
         {:ac-item (second item)})
  (show/assoc! component
               :results []
               :value ""
               :selected ((show/get-props component :result-selection-fn) item)))

(defn complete [component]
  (let [{:keys [results highlight-index]} (show/get-state component)]
    (if (not (empty? results))
      (selection component (nth results highlight-index)))))

(defn input-wire [component]
  (w/taps (w/wire)
    :focus-focus #(show component)
    :focus-blur  #(hide component)
    :form-change #(input-change component (:value %))
    :keyboard-down
      (fn [evt]
        (case (get-in evt [:criteria :keypress])
          :tab (if (show/get-props component :tab-insert) (complete component))
          :enter (complete component)
          nil))
    :keyboard-up
      (fn [evt]
        (case (get-in evt [:criteria :keypress])
          :esc (do (input-change component "")
                   (hide component))
          :up-arrow (hover-change component
                                  (show/get-state component :up-arrow-fn))
          :down-arrow (hover-change component
                                    (show/get-state component :down-arrow-fn))
          nil))))
(defn results-wire [component]
  (w/taps (w/wire)
    {:key :mouse-click} #(selection component (:item %))))

(show/defclass Autocomplete
  "Can do some autocomplete here son"
  [component]
  (default-props []
    {:wire                (w/wire)
     :input-component     DefaultInput
     :results-component   DefaultResults
     :item-component      DefaultItem
     :header-component    DefaultHeader
     :results-fn          autocomplete-fn
     :tab-insert          false
     :result-selection-fn identity})
  (will-mount []
    (when (= :above (show/get-props component :direction))
      (show/assoc! component
                   :results-mod-fn reverse
                   :up-arrow-fn inc
                   :down-arrow-fn dec)
      (show/update-in! component [:parent-class-name]
                       #(str % " above"))))
  (initial-state []
    {:value      (or (show/get-props component :value) "")
     :results    []
     :loading    false
     :selected   nil
     :highlight-index 0
     :active     false

     :results-mod-fn    (or (show/get-props component :results-mod-fn) identity)
     :up-arrow-fn       (or (show/get-props component :up-arrow-fn) dec)
     :down-arrow-fn     (or (show/get-props component :down-arrow-fn) inc)
     :parent-class-name (or (show/get-props component :parent-class-name) "autocomplete")})
  (render [{:as params
            :keys [input-component results-component item-component
                   header-component]}
           {:as state
            :keys [parent-class-name value selected results highlight-index active
                   results-mod-fn]}]
    (dom/div {:key "parent" :className parent-class-name}
      (input-component
        {:local-wire (input-wire component)
         :key "input"
         :value value
         :selected selected})
      (results-component
        {:local-wire (results-wire component)
         :key "results"
         :items results
         :value value
         :results-mod-fn results-mod-fn
         :highlight-index highlight-index
         :item-component item-component
         :header-component header-component
         :active active}))))
