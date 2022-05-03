(ns luhmann.core
  (:require
    [clojure.string :as string]))

;; Config as of the last system start
(defonce config (atom nil))


(def luhmann-dir-prefix ".luhmann")

(def site-dir-prefix ".luhmann/site")


(defn root-dir
  "Returns the root directory for the Zettelkasten.
  "
  []
  (:root-dir @config))


(defn luhmann-dir
  "Returns the directory where Luhmann keeps its files.
  "
  []
  (str (root-dir) "/" luhmann-dir-prefix))


(defn site-dir
  []
  (str (luhmann-dir) "/site"))

(defn site-path?
  "Returns true if the given path (relative to the root) is
  part of the generated web site.
  "
  [rel-path]
  (when rel-path
    (string/starts-with? rel-path site-dir-prefix)))

(defn site-rel-path
  "Strips the site prefix from the given path, returning the path
  relative to the base web site.
  "
  [rel-path]
  (string/replace rel-path (str site-dir-prefix "/") ""))
