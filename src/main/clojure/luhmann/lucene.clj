(ns luhmann.lucene
  (:require
    [babashka.fs :as fs]
    [clojure.string :as string]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.watcher :as watcher]
    )
  (:import
    [org.apache.lucene.analysis.standard StandardAnalyzer]
    [org.apache.lucene.document Document Field$Store StringField TextField]
    [org.apache.lucene.index DirectoryReader IndexWriter IndexWriterConfig IndexWriterConfig$OpenMode Term]
    [org.apache.lucene.queryparser.classic MultiFieldQueryParser]
    [org.apache.lucene.search IndexSearcher ScoreDoc]
    [org.apache.lucene.store FSDirectory]
    [org.jsoup Jsoup]))

(set! *warn-on-reflection* true)

(defn lucene-dir
  []
  (str (fs/path (luhmann/luhmann-dir) "lucene")))


(defn parse-html
  "Parses the HTML in the given path.

  Returns a map with the following fields:

  :title | Title of the document
  :body  | String representing the body text of the document, not including tags, footers, etc."
  [path]
  (let [doc (Jsoup/parse (fs/file path) "utf-8")
        content (some-> doc
                        (.getElementById "content")
                        (.text))]
    {:title (.title doc)
     :body content}))

#_(parse-html (fs/path (luhmann/site-dir) "index.html"))


(def writer-lock (Object.))

(defn index-file
  "Indexes a file with the given relative path.
  "
  [^String rel-path]
  (if (string/ends-with? (str rel-path) ".html")
    (locking writer-lock
      (log/info "Indexing {}" rel-path)
      (let [config (-> (IndexWriterConfig.)
                       (.setOpenMode IndexWriterConfig$OpenMode/CREATE_OR_APPEND))
            {:keys [title body]} (parse-html (fs/path (luhmann/site-dir) rel-path))
            doc (doto (Document.)
                  (.add (StringField. "path" rel-path Field$Store/YES))
                  (.add (TextField. "title" title Field$Store/YES))
                  (.add (TextField. "body" body Field$Store/YES)))]
        (with-open [dir (FSDirectory/open (fs/path (lucene-dir)))
                    writer (IndexWriter. dir config)]
          (.updateDocument writer (Term. "path" rel-path) doc))))
    (log/info "Skipping indexing of {}" rel-path)))

#_(index-file "index.html")
#_(index-file "fruit/apple.html")
#_(index-file "fruit/orange.html")


(defn rebuild-index
  []
  (log/info "Rebuilding index")
  (println "Rebuilding index")
  (fs/delete-tree (lucene-dir))
  (let [site-dir (luhmann/site-dir)
        i (inc (count site-dir))]
    (doseq [path (fs/glob site-dir "**.html")]
      (let [rel-path (subs (str path) i)]
        (index-file rel-path)))))


(defn delete-file
  "Deletes a file with the given relative path from the index.
  "
  [^String rel-path]
  (when (string/ends-with? (str rel-path) ".html")
    (locking writer-lock
      (log/info "Deleting {} from the index" rel-path)
      (let [config (-> (IndexWriterConfig.)
                       (.setOpenMode IndexWriterConfig$OpenMode/CREATE_OR_APPEND))]
        (with-open [dir (FSDirectory/open (fs/path (lucene-dir)))
                    writer (IndexWriter. dir config)]
          (.deleteDocuments writer ^"[Lorg.apache.lucene.index.Term;" (into-array [(Term. "path" rel-path)])))))))

#_(delete-file "index.html")
#_(delete-file "fruit/apple.html")

(defn search
  [^String q]
  (with-open [dir (FSDirectory/open (fs/path (lucene-dir)))
              reader (DirectoryReader/open dir)]
    (let [searcher (IndexSearcher. reader)
          ;; TODO maybe boost title a bit?
          query-parser (MultiFieldQueryParser. (into-array ["title" "body"]) (StandardAnalyzer.))
          query (.parse query-parser q)
          ]
      (mapv (fn [^ScoreDoc score-doc]
              (let [doc (.doc searcher (.-doc score-doc))]
                {:score (.-score score-doc)
                 :path (.get doc "path")
                 :title (.get doc "title")}))
            (-> searcher
                (.search query 20)
                (.-scoreDocs))))))

#_(search "apple")
#_(search "orange")
#_(search "demo*")
#_(search "blossom")
#_(search "edible")


(watcher/reg-listener
  :lucene
  (fn [{:keys [event path]}]
    (if (= :delete event)
      (delete-file (luhmann/site-rel-path path))
      (index-file (luhmann/site-rel-path path)))))
