(ns build
  (:require
    [clojure.tools.build.api :as b]))

(def lib 'ca.krasnay/luhmann)
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def java-class-dir "target/java-classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s.jar" (name lib) version))

(defn clean
  [_]
  (b/delete {:path "target"}))


(defn compile-java
  [_]
  (b/javac {:src-dirs ["src/main/java"]
            :class-dir java-class-dir
            :basis basis
            ;:javac-opts ["-source" "8" "-target" "8"]
            }))


(defn uberjar
  [_]
  (clean nil)
  (compile-java nil)
  (b/copy-dir {:src-dirs ["src/main/clojure" "src/main/resources" java-class-dir]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src/main/clojure"]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'luhmann.main}))
