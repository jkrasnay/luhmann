(ns luhmann.asciidoc
  (:require
    [babashka.fs :as fs]
    [clojure.string :as string]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.watcher :as watcher])
  (:import
    [org.asciidoctor Asciidoctor$Factory Options]))

(defonce asciidoctor (atom nil))

(defn options
  [dest-file]
  (doto (Options.)
    (.setMkDirs true)
    (.setToFile (str dest-file))))


(defn convert-file
  ([path]
   (convert-file path @asciidoctor (luhmann/root-dir) (luhmann/site-dir)))
  ([path asciidoctor root-dir site-dir]
   (when-not (string/starts-with? path luhmann/luhmann-dir-prefix)
     (let [src (fs/file root-dir path)]
       (if (.endsWith path ".adoc")
         (let [dest (fs/file site-dir (string/replace path #".adoc$" ".html"))]
           (log/info "Converting file {}" path)
           (.convertFile asciidoctor src (options dest)))
         (let [dest (fs/file site-dir path)]
           (log/info "Copying file {}" path)
           (fs/create-dirs (fs/parent dest))
           (fs/copy src dest {:replace-existing true})))))))


(defn build-site
  ([]
   (build-site (luhmann/root-dir) (luhmann/site-dir)))
  ([root-dir site-dir]
   (log/info "Building site in {}" site-dir)
   (println "Rebuilding web site")
   (fs/delete-tree site-dir)
   (->> (fs/glob root-dir "**")
        (filter #(not (fs/directory? %)))
        (map #(convert-file (str (fs/relativize root-dir %))))
        doall)))


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
  (.requireLibrary @asciidoctor (into-array ["asciidoctor-diagram"]))
  (build-site))


;;============================================================
;; Registrations
;;

(watcher/reg-listener
  :asciidoc
  (fn [{:keys [event path]}]
    (when (#{:create :modify} event)
      (convert-file path))))
