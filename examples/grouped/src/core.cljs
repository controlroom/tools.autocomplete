(ns examples.grouped.core
  (:require [show.core :as show :include-macros true]
            [show.dom  :as dom  :include-macros true]
            [ctrlrm.autocomplete :refer [Autocomplete]]))

(def chars (into [] "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"))

(defn rand-char []
  (nth chars (rand-int (count chars))))

(defn rand-word []
  (apply str (take (inc (rand-int 10)) (repeatedly rand-char))))

(def random-words
  (into []
        (map (fn [w i] {:index i :word w :count (count w)})
             (sort (into [] (take 500 (repeatedly rand-word))))
             (range))))

(defn local-ac-fn [text result-fn]
  (if (= text "")
    (result-fn [])
    (result-fn
      (group-by
        (fn [{:keys [word]}]
          {:title (count word)
           :pos (- 100 (count word))})
        (take 5 (filter #(re-find (re-pattern (str "(?i)^" text))
                                  (:word %))
                        random-words))))))

(show/defclass RandomWordItem [component]
  (render [props _]
    (dom/span (:word props))))

(show/defclass What [component]
  (will-mount
    (js/setTimeout (fn [] (show/force-update! component)) 100))
  (render [props state]
          (Autocomplete {:results-fn local-ac-fn
                         :result-selection-fn (fn [item] (:word item))
                         :item-component RandomWordItem})))

(show/render-component
  (What)
  (.getElementById js/document "ac1"))
