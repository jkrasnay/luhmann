(ns luhmann.asciidoc
  (:require
    [babashka.fs :as fs]
    [clojure.string :as string]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.watcher :as watcher])
  (:import
    [org.asciidoctor Asciidoctor Asciidoctor$Factory Options SafeMode]))

(defonce asciidoctor (atom nil))


(defn replace-ext
  [path new-ext]
  (-> path
      fs/split-ext
      first
      (str "." new-ext)))

#_(replace-ext "foo/bar/baz.adoc" "html")


(defn options
  ^Options
  [dest-file]
  (doto (Options.)
    (.setMkDirs true)
    (.setSafe SafeMode/UNSAFE)
    (.setToFile (str dest-file))))


(defn convert-file!
  [rel-path]
  (let [src (fs/file (luhmann/root-dir) rel-path)]
    (cond

      (= luhmann/config-file (str rel-path))
      (log/info "Skipping config file")

      (= "adoc" (fs/extension rel-path))
      (let [dest (fs/file (luhmann/site-dir) (replace-ext rel-path "html"))]
        (log/info "Converting file {}" rel-path)
        (.convertFile ^Asciidoctor @asciidoctor
                      src
                      (options dest)))

      :else
      (let [dest (fs/file (luhmann/site-dir) rel-path)]
        (log/info "Copying file {}" rel-path)
        (fs/create-dirs (fs/parent dest))
        (fs/copy src dest {:replace-existing true})))))


(defn find-files
  [root-dir exclude-dir]
  (->> (fs/glob root-dir "**")
       (remove #(or (fs/directory? %)
                    (fs/starts-with? % exclude-dir)))
       (map #(str (fs/relativize root-dir %)))))

#_(find-files "example" "example/.luhmann")

(defn build-site!
  []
  (let [site-dir (luhmann/site-dir)]
    (log/info "Building site in {}" site-dir)
    (println "Rebuilding web site")
    (fs/delete-tree site-dir)
    (doseq [rel-path (find-files (luhmann/root-dir) (luhmann/luhmann-dir))]
      (convert-file! rel-path))))


;;============================================================
;; start/stop
;;

(defn stop
  []
  (when @asciidoctor
    (.shutdown ^Asciidoctor @asciidoctor)
    (reset! asciidoctor nil)))


(defn start
  [_config]
  (stop)
  (reset! asciidoctor (Asciidoctor$Factory/create))
  (.requireLibrary ^Asciidoctor @asciidoctor (into-array ["asciidoctor-diagram"]))
  (build-site!))


;;============================================================
;; Registrations
;;

(watcher/reg-listener
  :asciidoc
  (fn [{:keys [event path]}]
    (let [src (fs/path (luhmann/root-dir) path)]
      (when-not (fs/starts-with? src (luhmann/luhmann-dir))
        (cond

          (#{:create :modify} event)
          (convert-file! path)

          (= :delete event)
          (let [dest (fs/path (luhmann/site-dir)
                              (if (= "adoc" (fs/extension path))
                                (replace-ext path "html")
                                path))]
            (log/info "Deleting {}" dest)
            (fs/delete dest)))))))
