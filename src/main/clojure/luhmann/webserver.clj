(ns luhmann.webserver
  (:require
    [luhmann.core :as luhmann]
    [org.httpkit.server :refer [run-server]]
    [ring.middleware.file :refer [wrap-file]]))


(defonce server (atom nil))


(defn test-handler
  [_req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world</h1>"})


(defn stop
  []
  (when @server
    (@server)
    (reset! server nil)))

(defn start
  [_config]
  (stop)
  (run-server (-> test-handler
                  (wrap-file (luhmann/site-dir)))
              {:port 2022}))
