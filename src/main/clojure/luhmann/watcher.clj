(ns luhmann.watcher
  (:require
    [babashka.fs :as fs]
    [luhmann.core :as luhmann]
    [luhmann.log :as log])
  (:import
    [io.methvin.watcher DirectoryChangeEvent DirectoryChangeListener DirectoryWatcher]))

(defonce watcher (atom nil))

(defonce listeners (atom {}))


(def event-map
  {"MODIFY" :modify
   "CREATE" :create
   "DELETE" :delete})


(defn reg-listener
  "Registers a listener with the watcher.

  `k` is a key that uniquely identifies the listener.
  `f` is a function that is called upon an fs change with a map with the following keys:

  :event | One of :create, :modify, or :delete
  :path  | Full path of the file that changed
  "
  [k f]
  (swap! listeners assoc k f))


(defn handle-event
  [^DirectoryChangeEvent e]
  (try
    (let [event-type (get event-map (str (.eventType e)))
          dir? (.isDirectory e)
          path (-> e .path str)]
      (when (and event-type (not dir?)
                 ;; We watch the root dir and site dir but exclude others
                 ;; under the .luhmann directory (logs, index, etc.)
                 (or (fs/starts-with? path (luhmann/site-dir))
                     (not (fs/starts-with? path (luhmann/luhmann-dir)))))
        (log/info "File changed: {} {}" event-type path)
        (doseq [f (vals @listeners)]
          (f {:event event-type
              :path path}))))
    (catch Exception ex
      (log/error "Error in watcher/handle-event" ex))))


(defn stop
  []
  (when @watcher
    (log/info "Stopping watcher")
    (.close ^DirectoryWatcher @watcher)
    (reset! watcher nil)))


(defn start
  [_config]
  (stop)
  (log/info "Starting watcher {}" (luhmann/root-dir))
  (let [paths (if (fs/starts-with? (luhmann/luhmann-dir) (luhmann/root-dir))
                [(fs/path (luhmann/root-dir))]
                [(fs/path (luhmann/root-dir)) (fs/path (luhmann/luhmann-dir))])]
    (reset! watcher (doto (-> (DirectoryWatcher/builder)
                              (.paths paths)
                              (.listener (reify DirectoryChangeListener
                                           (onEvent [_ e]
                                             (handle-event e))))
                              (.build))
                      (.watchAsync)))))

#_(start nil)
#_(stop)
