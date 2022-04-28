(ns luhmann.asciidoc
  (:require
    [luhmann.core :as luhmann]
    [luhmann.log :as log])
  (:import
    [org.asciidoctor Asciidoctor$Factory Options]
    [org.asciidoctor.jruby AsciiDocDirectoryWalker]))

(defonce asciidoctor (atom nil))

(defn site-dir
  []
  (str (luhmann/luhmann-dir) "/site"))


(defn build-site
  ([]
   (build-site @asciidoctor (luhmann/root-dir) (site-dir)))
  ([asciidoctor root-dir site-dir]
   (log/info "Building site in {}" site-dir)
   (let [files (AsciiDocDirectoryWalker. root-dir)
        options (doto (Options.)
                  (.setMkDirs true)
                  (.setToDir site-dir))]
    (.convertDirectory asciidoctor files options))))


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
  (build-site))
