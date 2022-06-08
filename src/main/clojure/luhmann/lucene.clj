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
    [org.apache.lucene.search IndexSearcher Query ScoreDoc]
    [org.apache.lucene.search.highlight Highlighter QueryScorer SimpleHTMLFormatter TextFragment TokenSources]
    [org.apache.lucene.store FSDirectory]
    [org.jsoup Jsoup]))

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
    {:title (or (.title doc) "Luhmann")
     :body (or content "(unknown)")}))

#_(parse-html (fs/path (luhmann/site-dir) "index.html"))


(def writer-lock (Object.))

(defn index-file
  "Indexes a file with the given relative path.
  "
  [^String rel-path]
  (if (= "html" (fs/extension rel-path))
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
  (when (= "html" (fs/extension rel-path))
    (locking writer-lock
      (log/info "Deleting {} from the index" rel-path)
      (let [config (-> (IndexWriterConfig.)
                       (.setOpenMode IndexWriterConfig$OpenMode/CREATE_OR_APPEND))]
        (with-open [dir (FSDirectory/open (fs/path (lucene-dir)))
                    writer (IndexWriter. dir config)]
          (.deleteDocuments writer ^"[Lorg.apache.lucene.index.Term;" (into-array [(Term. "path" rel-path)])))))))

#_(delete-file "index.html")
#_(delete-file "fruit/apple.html")

(defn summarize
  "Returns a fragment of HTML that summarizes the given text, surrounding
  relevant terms with bold tags.
  "
  [doc-id ^Query query ^IndexSearcher searcher]
  (let [formatter (SimpleHTMLFormatter.)
        highlighter (Highlighter. formatter (QueryScorer. query))
        analyzer (StandardAnalyzer.)
        token-stream (TokenSources/getAnyTokenStream (.getIndexReader searcher) doc-id "body" analyzer)
        text (-> searcher
                 (.doc doc-id)
                 (.get "body"))]
    (->> (.getBestTextFragments highlighter token-stream text false 10)
         (filter (fn [^TextFragment frag]
                   (and (some? frag)
                        (pos? (.getScore frag)))))
         (map str)
         (string/join "..."))))


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
              (let [doc-id (.-doc score-doc)
                    doc (.doc searcher doc-id)]
                {:score (.-score score-doc)
                 :path (.get doc "path")
                 :title (.get doc "title")
                 :summary-html (summarize doc-id query searcher)}))
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
    (when (fs/starts-with? path (luhmann/site-dir))
      (let [rel-path (str (fs/relativize (luhmann/site-dir) path))]
        (if (= :delete event)
          (delete-file rel-path)
          (index-file rel-path))))))
