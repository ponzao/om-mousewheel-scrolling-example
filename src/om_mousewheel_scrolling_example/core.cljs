(ns ^:figwheel-always om-mousewheel-scrolling-example.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(enable-console-print!)

(def size 10000)

(def first-names
  ["Vesa" "Mikko" "Hannu" "Antti" "Sami" "Lassi" "Timo" "Mika" "Tuomas" "Simo" "Emmi" "Leo"
   "Sampo" "Rami" "Esko" "Markus"])

(def last-names
  ["Marttila" "Pakarinen" "Leinonen" "Nupponen" "Aurejärvi" "Immonen" "Westkämper" "Kivimäki"
   "Järvensivu" "Råman" "Sallinen" "Heng" "Seppäläinen" "Karjula" "Vääräsmäki" "Melander"])

(defn generate-data
  []
  (vec (take size (repeatedly (fn []
                                {:first-name (rand-nth first-names)
                                 :last-name (rand-nth last-names)
                                 :value 0})))))

(defonce app-state
  (atom {:people (vec (map-indexed (fn [i x]
                                     (assoc x :rank (inc i)))
                                   (generate-data)))}))

(defonce interval
  (let [updater (fn []
                  (swap! app-state (fn [state]
                                     (let [modified-state (reduce (fn [acc x]
                                                                    (assoc-in acc [:people x :value] (* (rand) (rand-int 100000))))
                                                                  state
                                                                  (take 1000 (repeatedly #(rand-int size))))]
                                       (update-in modified-state [:people] (fn [people]
                                                                             (vec
                                                                               (map-indexed (fn [i x]
                                                                                              (assoc x :rank (inc i)))
                                                                                            (sort-by :value > people)))))))))]
    (updater)
    (.setInterval js/window updater 5000)))

(defn row-view
  [data owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [rank first-name last-name value]} data]
        (dom/tr nil
                (dom/td nil rank)
                (dom/td nil first-name)
                (dom/td nil last-name)
                (dom/td nil value))))))

(defn table-view
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [offset 0
            limit 30]
        {:limit limit
         :offset offset}))

    om/IRenderState
    (render-state [_ {:keys [limit offset] :as state}]
      (let [begin offset
            end (+ offset limit)]
        (dom/div #js {:className "container"}
                 (dom/table nil
                            (dom/thead nil
                                       (dom/tr nil
                                               (dom/th nil "Rank")
                                               (dom/th nil "First name")
                                               (dom/th nil "Last name")
                                               (dom/th nil "Value")))
                            (apply dom/tbody #js
                                   {:onWheel (fn [e]
                                               (if (neg? e.deltaY)
                                                 (om/update-state! owner :offset (fn [offset]
                                                                                   (if-not (zero? offset)
                                                                                     (dec offset)
                                                                                     offset)))
                                                 (om/update-state! owner :offset (fn [offset]
                                                                                   (if-not (>= (+ offset limit) (count data))
                                                                                     (inc offset)
                                                                                     offset)))))}
                                   (om/build-all row-view (subvec data begin end)))))))))

(defn app-view
  [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "container"}
               (dom/h1 nil "Data")
               (om/build table-view (:people data))))))

(om/root
  app-view
  app-state
  {:target (. js/document (getElementById "app"))})


