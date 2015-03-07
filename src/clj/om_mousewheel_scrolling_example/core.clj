(ns om-mousewheel-scrolling-example.core
  (:require
    [clojure.core.async :refer [go-loop put! <! chan timeout mult tap <!!]]
    [org.httpkit.server :refer [with-channel websocket? on-receive send! run-server]]))

(def size 200)

(def first-names
  ["Vesa" "Mikko" "Hannu" "Antti" "Sami" "Lassi" "Timo" "Mika" "Tuomas" "Simo" "Emmi" "Leo"
   "Sampo" "Rami" "Esko" "Markus"])

(def last-names
  ["Marttila" "Pakarinen" "Leinonen" "Nupponen" "Aurejärvi" "Immonen" "Westkämper" "Kivimäki"
   "Järvensivu" "Råman" "Sallinen" "Heng" "Seppäläinen" "Karjula" "Vääräsmäki" "Melander"])

(defn generate-data
  []
  (vec (map-indexed (fn [i x]
                      (assoc x :rank (inc i)))
                    (take size (repeatedly (fn []
                                             {:first-name (rand-nth first-names)
                                              :last-name (rand-nth last-names)
                                              :value 0}))))))

(def <state>
  (chan))

(def <clients>
  (mult <state>))

(defn async-handler
  [ring-request]
  (with-channel ring-request channel
    (when (websocket? channel)
      (let [<c> (tap <clients> (chan))]
        (go-loop []
          (when-let [data (<! <c>)]
            (send! channel (str data)))
          (recur))))))

(defn init
  []
  (go-loop [people (generate-data)]
    (let [modified-people (reduce (fn [acc x]
                                    (update-in acc [x :value] inc))
                                  people
                                  (take 1000 (repeatedly #(rand-int size))))
          ranked-people (vec
                          (map-indexed (fn [i x]
                                         (assoc x :rank (inc i)))
                                       (sort-by :value > modified-people)))]
      (put! <state> ranked-people)
      (<! (timeout 5000))
      (recur ranked-people))))

(run-server #'async-handler {:port 8080})

(init)

