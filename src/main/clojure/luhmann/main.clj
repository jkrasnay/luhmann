(ns luhmann.main
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [luhmann.asciidoc :as asciidoc]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.lucene :as lucene]
    [luhmann.watcher :as watcher]
    [luhmann.webserver :as webserver])
  [:gen-class])

(set! *warn-on-reflection* true)

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
  (lucene/rebuild-index)
  (webserver/start config)
  (watcher/start config)
  (reset! running true)
  (log/info "Luhmann started"))


;;============================================================
;; CLI
;;

(defn load-config
  [root-dir]
  (let [config-file (fs/file root-dir luhmann/config-file)]
    (if (fs/exists? config-file)
      (do (println "Loading config file" (str config-file))
          (-> (slurp config-file)
              (edn/read-string)))
      (do (println "Config file" (str config-file) "not found")
          nil))))


(defn -main
  [& args]
  (let [root-dir (first args)]
    (cond

      (nil? root-dir)
      (println "Usage: java -jar luhmann.jar root-dir")

      (not (fs/exists? root-dir))
      (println "Directory" root-dir "does not exist")

      (not (fs/directory? root-dir))
      (println root-dir "is not a directory")

      :else
      (let [root-dir (str (fs/canonicalize root-dir))
            config (merge {:port 2022}
                          (load-config root-dir)
                          {:root-dir root-dir})]
        (println "Starting Luhmann in" root-dir)
        (println "Config:" (pr-str config))
        (start config)
        (println (str "Luhmann started at http://localhost:" (:port config)))))))

#_
(stop)
#_
(start {:root-dir "/Users/john/ws/luhmann/example" :port 2022})
