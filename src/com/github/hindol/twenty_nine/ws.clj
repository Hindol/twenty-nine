(ns com.github.hindol.twenty-nine.ws
  (:require
   [clojure.core.async :as async])
  (:import
   (org.eclipse.jetty.websocket.api Session
                                    WebSocketAdapter)))

(def clients (atom {}))

(defn make-listener
  [_request _response ws-map]
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [^Session ws-session]
      (proxy-super onWebSocketConnect ws-session)
      (when-let [f (:on-connect ws-map)]
        (f ws-session)))
    (onWebSocketClose [status-code reason]
      (when-let [f (:on-close ws-map)]
        (f (.getSession this) status-code reason)))
    (onWebSocketError [^Throwable e]
      (when-let [f (:on-error ws-map)]
        (f (.getSession this) e)))

    (onWebSocketText [^String message]
      (when-let [f (:on-text ws-map)]
        (f (.getSession this) message)))
    (onWebSocketBinary [^bytes payload offset length]
      (when-let [f (:on-binary ws-map)]
        (f (.getSession this) payload offset length)))))

(defn broadcast!
  ([message] (broadcast! @clients message))
  ([clients message]
   (doseq [[^Session ws-session channel] clients]
     (when (.isOpen ws-session)
       (async/put! channel (pr-str message))))))

(let [heartbeat (atom nil)]
  (defn start-heartbeat
    []
    (when-not @heartbeat
      (reset! heartbeat
              (async/go-loop []
                (async/<! (async/timeout 15000))
                (broadcast! [:ping])
                (recur))))))

(defn add-client
  [session send-ch]
  (start-heartbeat)
  (swap! clients assoc session send-ch))
