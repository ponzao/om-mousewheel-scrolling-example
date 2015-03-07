(ns ^:figwheel-always om-mousewheel-scrolling-example.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require
    [cljs.core.async :refer [chan <! put!]]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [secretary.core :as secretary :refer-macros [defroute]]
    [goog.events :as events]
    [goog.history.EventType :as EventType])
  (:import goog.History))

(enable-console-print!)

(defonce <server>
  (let [<c> (chan)]
    (doto (js/WebSocket. "ws://localhost:8080")
      (set! -onmessage (fn [event]
                         (put! <c> event.data))))
    <c>))

(defonce app-state
  (atom {:scroll {:limit 20
                  :offset 0}
         :people []}))

(defonce server-loop
  (go-loop []
    (when-let [data (<! <server>)]
      (swap! app-state assoc-in [:people] (cljs.reader/read-string data))
      (recur))))

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


