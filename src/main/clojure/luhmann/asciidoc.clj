(ns luhmann.asciidoc
  (:require
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.watcher :as watcher])
  (:import
    [luhmann RefreshDocinfoProcessor]
    [org.asciidoctor Asciidoctor$Factory Options SafeMode]
    [org.asciidoctor.jruby AsciiDocDirectoryWalker]))

(defonce asciidoctor (atom nil))

(defn options
  [site-dir]
  (doto (Options.)
    (.setHeaderFooter true)
    (.setMkDirs true)
    (.setSafe SafeMode/SERVER)
    (.setToDir site-dir)))

(defn build-site
  ([]
   (build-site @asciidoctor (luhmann/root-dir) (luhmann/site-dir)))
  ([asciidoctor root-dir site-dir]
   (log/info "Building site in {}" site-dir)
   (let [files (AsciiDocDirectoryWalker. root-dir)
        options (options site-dir)]
    (.convertDirectory asciidoctor files options))))


(defn convert-file
  ([path]
   (convert-file path @asciidoctor (luhmann/root-dir) (luhmann/site-dir)))
  ([path asciidoctor root-dir site-dir]
   (when (.endsWith path ".adoc")
     (log/info "Converting file {}" path)
     (let [file (java.io.File. root-dir path)
           options (options site-dir)]
       (.convertFile asciidoctor file options)))))


;;============================================================
;; start/stop
;;

(defn stop
  []
  (when @asciidoctor
    (.shutdown @asciidoctor)
    (reset! asciidoctor nil)))


(defn start
  [_config]
  (stop)
  (reset! asciidoctor (Asciidoctor$Factory/create))
  (-> @asciidoctor
      .javaExtensionRegistry
      (.docinfoProcessor RefreshDocinfoProcessor))
  (build-site))


;;============================================================
;; Registrations
;;

(watcher/reg-listener
  :asciidoc
  (fn [{:keys [event path]}]
    (when (#{:create :modify} event)
      (convert-file path))))
