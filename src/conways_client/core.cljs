(ns conways-client.core
  (:require [monet.canvas :as canvas]
            [monet.core :refer [animation-frame]]
            [chord.client :refer [ws-ch]]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! >! put! close! chan timeout]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn draw! [monet-canvas app-state]
  (canvas/add-entity monet-canvas :background
                     (canvas/entity app-state
                                    nil
                                    (fn [ctx val]
                                      (let [{:keys [width height cell-size]} @val]
                                        (-> ctx
                                            (canvas/fill-style "#e6e6e6")
                                            (canvas/fill-rect {:w width :h height}))
                                        (canvas/stroke-style ctx "#999999")
                                        (canvas/begin-path ctx)
                                        (let [xs (range 0 (inc width) cell-size)
                                              ys (range 0 (inc height) cell-size)]
                                          (doseq [x xs]
                                            (canvas/move-to ctx x 0)
                                            (canvas/line-to ctx x height))
                                          (doseq [y ys]
                                            (canvas/move-to ctx 0 y)
                                            (canvas/line-to ctx width y)))
                                        (canvas/stroke ctx)))))

  (canvas/add-entity monet-canvas :cells
                     (canvas/entity app-state
                                    nil
                                    (fn [ctx val]
                                      (let [{:keys [cells cell-size]} @val]
                                        (canvas/fill-style ctx "#007799")
                                        (doseq [[x y] cells]
                                          (canvas/fill-rect ctx {:x (inc (* cell-size x)) :y (inc (* cell-size y)) :w (dec cell-size) :h (dec cell-size)})))))))

(defn disconnected [state]
  (println "disconnected"))

(defn handle-message [state {:strs [alive] :as message}]
  (swap! state assoc :cells alive))

(defn request-next-grid [ws-chan]
  (go (>! ws-chan {"next" 1})))

(defn app-loop [state ws-chan]
  (go-loop []
    (let [timeout-ch                           (timeout 500)
          [{:keys [message error] :as msg} ch] (alts! [timeout-ch ws-chan])]
      (if (= ch timeout-ch)
        (request-next-grid ws-chan)
        (when (and message (not error))
          (handle-message state message)))
      (if (or (= ch timeout-ch) msg)
        (recur)
        (disconnected state)))))

(defn restart-grid [state]
  (let [{:keys [width height cell-size]} @state]
    (go (>! (:ws-chan @state) {"start" {:width (quot width cell-size) :height (quot height cell-size)} }))))

(defn connected [state ws-chan]
  (println "connected")
  (swap! state assoc :ws-chan ws-chan)
  (restart-grid state)
  (app-loop state ws-chan))

(defn connection-error [state error]
  (println "Couldn't connect to server " error))

(goog-define dynamic-ws-port false)

(def default-ws-port 8080)

(defn ws-url []
  (gstring/format "ws://%s:%s/websocket" js/window.location.hostname (if dynamic-ws-port js/window.location.port default-ws-port)))

(defn connect! [state]
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch (ws-url) {:format :json}))]
      (if-not error
        (connected state ws-channel)
        (connection-error state error)))))

(defonce app-state (atom {:cells [] :ws-chan nil}))

(defn update-size [width height cell-size]
  (swap! app-state merge {:width width
                          :height height
                          :cell-size cell-size}))

(defn init-canvas! [width height cell-size]
  (let [canvas-dom   (.getElementById js/document "canvas")
        monet-canvas (canvas/init canvas-dom "2d")]
    (update-size width height cell-size)
    (draw! monet-canvas app-state)))

(defn ^:export start [width height cell-size]
  (println "start" width height cell-size)
  (init-canvas! width height cell-size)
  (swap! app-state merge {:width width
                          :height height
                          :cell-size cell-size}))

(defn ^:export resize [width height cell-size]
  (println "resize" width height cell-size)
  (update-size width height cell-size)
  (restart-grid app-state))

(defn on-js-reload []
  (.reload js/location true))

(connect! app-state)
