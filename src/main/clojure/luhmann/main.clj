(ns luhmann.main
  (:require
    [luhmann.asciidoc :as asciidoc]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.watcher :as watcher]
    [luhmann.webserver :as webserver]))

(defn stop
  []
  (log/info "Stopping Luhmann")
  (watcher/stop)
  (webserver/stop)
  (asciidoc/stop)
  (log/stop))

(defn start
  [config]
  (stop)
  (reset! luhmann/config config)
  (log/start config)
  (asciidoc/start config)
  (webserver/start config)
  (watcher/start config)
  (log/info "Luhmann started in {}" (luhmann/root-dir)))

#_(start {:root-dir "/Users/john/ws/luhmann/example"})
