(ns luhmann.main
  (:require
    [luhmann.log :as log]))

(defn stop
  []
  (log/info "Stopping Luhmann")
  (log/stop))

(defn start
  [config]
  (stop)
  (log/start config)
  (log/info "Luhmann started"))

#_(start {:root-dir "/Users/john/ws/luhmann/example"})
