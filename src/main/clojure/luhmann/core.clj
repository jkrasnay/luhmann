(ns luhmann.core)

(defn root-dir
  "Returns the root directory for the Zettelkasten.
  "
  [config]
  (:root-dir config))

(defn luhmann-dir
  "Returns the directory where Luhmann keeps its files.
  "
  [config]
  (str (root-dir config) "/.luhmann"))
