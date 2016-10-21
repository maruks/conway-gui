(ns conways-client.core
  (:require [monet.canvas :as canvas]
            [monet.core :refer [animation-frame]]
            [chord.client :refer [ws-ch]]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! >! put! close! chan timeout]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(defn on-js-reload []
  (println "reload"))

(def step 10)

(defn draw! [monet-canvas app-state]
  (canvas/add-entity monet-canvas :background
                     (canvas/entity {:x 0 :y 0 :w 1200 :h 600}
                                    nil
                                    (fn [ctx val]
                                      (-> ctx
                                          (canvas/fill-style "#e6e6e6")
                                          (canvas/fill-rect val))
                                      (canvas/stroke-style ctx "#999999")
                                      (canvas/begin-path ctx)
                                      (let [{:keys [x y w h]} val
                                            xs (range 0 (inc w) step)
                                            ys (range 0 (inc h) step)]
                                        (doseq [x xs]
                                          (canvas/move-to ctx x 0)
                                          (canvas/line-to ctx x h))
                                        (doseq [y ys]
                                          (canvas/move-to ctx 0 y)
                                          (canvas/line-to ctx w y)))
                                      (canvas/stroke ctx))))

  (canvas/add-entity monet-canvas :cells
                     (canvas/entity {:w 1200 :h 600}
                                    nil
                                    (fn [ctx val]
                                      (canvas/fill-style ctx "#009900")
                                      (doseq [[x y] (:cells @app-state)]
                                        (canvas/fill-rect ctx {:x (inc (* step x)) :y (inc (* step y)) :w 9 :h 9}))))))

(defn disconnected [state]
  (println "disconnected"))

(defn handle-message [state {:strs [alive] :as message}]
;  (println message)
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
  (reset! state {:cells [] :ws-chan ws-chan})
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

(defn init! []
  (let [canvas-dom   (.getElementById js/document "canvas")
        monet-canvas (canvas/init canvas-dom "2d")]
    (draw! monet-canvas app-state)
    (connect! app-state)))

(defn ^:export restart []
  (go (>! (:ws-chan @app-state) ["restart"]))
  (swap! app-state assoc :cells []))

(init!)
