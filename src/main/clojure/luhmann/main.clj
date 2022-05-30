(ns luhmann.main
  (:require
    [babashka.fs :as fs]
    [clojure.edn :as edn]
    [clojure.tools.cli :refer [parse-opts]]
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

(def cli-options
  [["-p" "--port=PORT" "Web server port number"
    :default 2022
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be a number between 0 and 65535"]]])


(defn load-config
  [root-dir]
  (let [config-file (fs/file root-dir luhmann/config-file)]
    (if (fs/exists? config-file)
      (do (println "Loading config file" config-file)
          (-> (slurp config-file)
              (edn/read-string)))
      (do (println "Config file" config-file "not found")
          nil))))


(defn -main
  [& args]
  (let [{:keys [options
                arguments
                summary
                errors]} (parse-opts args cli-options)
        root-path ^String (first arguments)
        root-dir (when root-path
                   (java.io.File. root-path))]
    (cond

      (seq errors)
      (do (doseq [e errors]
            (println e))
          (println)
          (println "Supported options:")
          (println)
          (println summary))

      (nil? root-dir)
      (do (println "Usage: java -jar luhmann.jar (options) root-dir")
          (println)
          (println "Supported options:")
          (println)
          (println summary))

      (not (.exists root-dir))
      (println "Directory" root-path "does not exist")

      (not (.isDirectory root-dir))
      (println root-path "is not a directory")

      :else
      (let [config (merge (load-config root-dir)
                          (select-keys options [:path])
                          {:root-dir (.getCanonicalPath root-dir)})]
        (println "Starting Luhmann in" (.getCanonicalPath root-dir))
        (println "Config:" (pr-str config))
        (start config)
        (println (str "Luhmann started at http://localhost:" (:port config)))))))

#_
(stop)
#_
(start {:root-dir "/Users/john/ws/luhmann/example" :port 2022})
