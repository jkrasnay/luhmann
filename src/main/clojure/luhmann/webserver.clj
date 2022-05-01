(ns luhmann.webserver
  (:require
    [clojure.string :as string]
    [luhmann.core :as luhmann]
    [luhmann.watcher :as watcher]
    [org.httpkit.server :refer [as-channel run-server send!]]
    [ring.middleware.file :refer [wrap-file]]))


(defonce server (atom nil))

(defonce channels (atom #{}))

(defn handler
  [req]
  (cond

    (= "/ws" (:uri req))
    (as-channel req {:on-open (fn [ch]
                                (swap! channels conj ch))
                     :on-close (fn [ch _]
                                 (swap! channels disj ch))})

    :else
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "Not found"}))


(defn stop
  []
  (when @server
    (@server)
    (reset! server nil)))

(defn start
  [config]
  (stop)
  (reset! server (run-server (-> handler
                                 (wrap-file (luhmann/site-dir)))
                             {:port (:port config)})))


;;============================================================
;; Registrations
;;

(defn reload-browser
  []
  (doseq [ch @channels]
    (send! ch "reload")))

(watcher/reg-listener
  :webserver
  (fn [{:keys [event path]}]
    (let [full-path (str (java.io.File. (luhmann/root-dir) path))]
      (when (and (#{:create :modify} event)
                 (string/starts-with? full-path (luhmann/site-dir)))
        (reload-browser)))))
