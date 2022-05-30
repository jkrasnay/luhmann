(ns luhmann.core)

(def config-file ".luhmann.edn")


;; Config as of the last system start
(defonce config (atom nil))


(defn root-dir
  "Returns the root directory for the Zettelkasten.
  "
  []
  (:root-dir @config))


(defn luhmann-dir
  "Returns the directory where Luhmann keeps its files.
  "
  []
  (str (root-dir) "/.luhmann"))


(defn site-dir
  []
  (str (luhmann-dir) "/site"))


