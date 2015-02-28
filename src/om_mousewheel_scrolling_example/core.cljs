(ns ^:figwheel-always om-mousewheel-scrolling-example.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:import goog.History))

(enable-console-print!)

(def size 200)

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
  (atom {:scroll {:limit 20
                  :offset 0}
         :people (vec (map-indexed (fn [i x]
                                     (assoc x :rank (inc i)))
                                   (generate-data)))}))

(defonce history
  (History.))

(defroute base-route "/:offset" {:as params}
  (when-let [offset (:offset params)]
    (swap! app-state
           (fn [state]
             (let [offset (js/parseInt offset 10)
                   limit (get-in state [:scroll :limit])
                   n (count (:people state))]
               (if (or (neg? offset)
                       (> (+ offset limit) n))
                 state
                 (do
                   (.setToken history offset)
                   (assoc-in state [:scroll :offset] offset))))))))

(defonce interval
  (let [updater (fn []
                  (swap! app-state
                         (fn [state]
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

(defn scroll-to
  [offset]
  (secretary/dispatch! (str "/" offset)))

(defn table-view
  [data owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [offset limit]} (:scroll data)
            begin offset
            end (min (+ offset limit) (count (:people data)))]
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
                                                 (scroll-to (dec offset))
                                                 (scroll-to (inc offset))))}
                                   (om/build-all row-view (subvec (:people data) begin end)))))))))

(defn app-view
  [data owner]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "container"}
               (dom/h1 nil "Data")
               (om/build table-view data)))))

(om/root
  app-view
  app-state
  {:target (. js/document (getElementById "app"))})

(secretary/set-config! :prefix "#")

(goog.events/listen history EventType/NAVIGATE #(secretary/dispatch! (.-token %)))
(doto history (.setEnabled true))


