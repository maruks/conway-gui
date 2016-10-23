(ns conways-client.core
  (:require [monet.canvas :as canvas]
            [monet.core :refer [animation-frame]]
            [chord.client :refer [ws-ch]]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! >! put! close! chan timeout]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn draw! [monet-canvas app-state width height cell-size]
  (canvas/add-entity monet-canvas :background
                     (canvas/entity {:x 0 :y 0 :w width :h height}
                                    nil
                                    (fn [ctx val]
                                      (-> ctx
                                          (canvas/fill-style "#e6e6e6")
                                          (canvas/fill-rect val))
                                      (canvas/stroke-style ctx "#999999")
                                      (canvas/begin-path ctx)
                                      (let [{:keys [x y w h]} val
                                            xs (range 0 (inc w) cell-size)
                                            ys (range 0 (inc h) cell-size)]
                                        (doseq [x xs]
                                          (canvas/move-to ctx x 0)
                                          (canvas/line-to ctx x h))
                                        (doseq [y ys]
                                          (canvas/move-to ctx 0 y)
                                          (canvas/line-to ctx w y)))
                                      (canvas/stroke ctx))))

  (canvas/add-entity monet-canvas :cells
                     (canvas/entity {:w width :h height}
                                    nil
                                    (fn [ctx val]
                                      (canvas/fill-style ctx "#007799")
                                      (doseq [[x y] (:cells @app-state)]
                                        (canvas/fill-rect ctx {:x (inc (* cell-size x)) :y (inc (* cell-size y)) :w (dec cell-size) :h (dec cell-size)}))))))

(defn disconnected [state]
  (println "disconnected"))

(defn handle-message [state {:strs [alive] :as message}]
  (swap! state assoc :cells alive))

(defn app-loop [state ws-chan]
  (go-loop []
    (let [{:keys [message error] :as msg} (<! ws-chan)]
      (if-not error
        (when message
          (handle-message state message)))
      (if msg
        (recur)
        (disconnected state)))))

(defn connected [state ws-chan]
  (println "connected")
  (swap! state assoc :ws-chan ws-chan)
  (let [{:keys [width height]} @state]
    (go (>! ws-chan {"start" {"width" width "height" height}})))
  (app-loop state ws-chan))

(defn connection-error [state error]
  (println "Couldn't connect to server " error))

(def ws-port 8080)

(defn ws-url []
  (gstring/format "ws://%s:%s/websocket" js/window.location.hostname ws-port))

(defn connect! [state]
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch (ws-url) {:format :json}))]
      (if-not error
        (connected state ws-channel)
        (connection-error state error)))))

(defonce app-state (atom {:cells [] :ws-chan nil}))

(defn init-canvas! [width height cell-size]
  (let [canvas-dom   (.getElementById js/document "canvas")
        monet-canvas (canvas/init canvas-dom "2d")]
    (draw! monet-canvas app-state width height cell-size)))

(defn ^:export start [width height cell-size]
  (init-canvas! width height cell-size)
  (swap! app-state merge {:width (quot width cell-size)
                          :height (quot height cell-size)}))

(defn on-js-reload []
  (.reload js/location true))

(connect! app-state)
