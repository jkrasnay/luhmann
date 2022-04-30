(ns luhmann.main
  (:require
    [luhmann.asciidoc :as asciidoc]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.watcher :as watcher]
    [luhmann.webserver :as webserver])
  [:gen-class])

(defonce running (atom false))

(defn stop
  []
  (log/info "Stopping Luhmann")
  (watcher/stop)
  (webserver/stop)
  (asciidoc/stop)
  (reset! running false)
  (log/info "Luhmann stopped"))

(defn start
  [config]
  (when @running
    (stop))
  (reset! luhmann/config config)
  (log/configure config)
  (log/info "Starting Luhmann in {}" (luhmann/root-dir))
  (asciidoc/start config)
  (webserver/start config)
  (watcher/start config)
  (reset! running true)
  (log/info "Luhmann started"))

(defn -main
  [& args]
  (let [root-path (first args)
        root-dir (when root-path
                   (java.io.File. root-path))]
    (cond

      (nil? root-dir)
      (println "Usage: java -jar luhmann.jar root-dir")

      (not (.exists root-dir))
      (println "File" root-path "does not exist")

      :else
      (do
        (println "Starting Luhmann in" (.getCanonicalPath root-dir))
        (start {:root-dir (.getCanonicalPath root-dir)})
        (println "Luhmann started at http://localhost:2022")))))


#_(start {:root-dir "/Users/john/ws/luhmann/example"})
