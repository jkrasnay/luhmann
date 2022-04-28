(ns luhmann.main
  (:require
    [luhmann.asciidoc :as asciidoc]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]))

(defn stop
  []
  (log/info "Stopping Luhmann")
  (log/stop))

(defn start
  [config]
  (stop)
  (reset! luhmann/config config)
  (log/start config)
  (asciidoc/start config)
  (log/info "Luhmann started in {}" (luhmann/root-dir)))

#_(start {:root-dir "/Users/john/ws/luhmann/example"})
