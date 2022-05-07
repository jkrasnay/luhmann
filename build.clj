(ns build
  (:require
    [clojure.tools.build.api :as b]))


(def lib 'ca.krasnay/luhmann)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s.jar" (name lib) version))


(defn clean
  [_]
  (b/delete {:path "target"}))


(defn uberjar
  [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src/main/clojure" "src/main/resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src/main/clojure"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'luhmann.main}))
