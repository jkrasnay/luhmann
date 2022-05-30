(ns luhmann.api
  (:require
    [babashka.fs :as fs]
    [babashka.process :refer [process]]
    [cheshire.core :as cheshire]
    [clojure.string :as string]
    [luhmann.core :as luhmann]
    [luhmann.log :as log]
    [luhmann.lucene :as lucene]))


(defn ok-json
  [response-body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (cheshire/generate-string response-body)})


(defn config-handler
  "Returns configuration information for this instance.
  "
  [_req]
  (ok-json @luhmann/config))


(defn edit-handler
  "Invokes the configured editor with a note ID.

  POST /api/edit?note=path/to/note.adoc
  "
  [req]
  (let [note (get-in req [:query-params "note"])
        root-dir (luhmann/root-dir)
        path (fs/normalize (fs/path root-dir note))]
    (cond

      ; Check the caller isn't trying to escape from the Luhmann dir
      (not (fs/starts-with? path root-dir))
      {:status 404}

      :else
      (let [editor (:editor @luhmann/config)]
        (if (nil? editor)
          (do (log/info ":editor not configured")
              {:status 400
               :headers {"Content-Type" "text/plain"}
               :body (str "Editor launched: " path)})
          (try (log/info "Launching editor {} {}" editor (str path))
               (process [editor (str path)] {:dir root-dir})
               {:status 200
                :headers {"Content-Type" "text/plain"}
                :body (str "Editor launched: " path)}
               (catch Exception e
                 (log/error "Error starting editor" e)
                 {:status 400
                  :headers {"Content-Type" "text/plain"}
                  :body (.getMessage e)})))))))


(defn search-handler
  "Searches for documents.
  "
  [req]
  (let [q (get-in req [:query-params "q"])
        results (when-not (string/blank? q)
                  (lucene/search q))]
    (if results
      (ok-json results)
      (ok-json []))))


(defn api-handler
  "Dispatcher for API requests.
  "
  [req]
  (condp = [(:request-method req) (:uri req)]
    [:get "/api/config"] (config-handler req)
    [:get "/api/edit"]   (edit-handler req)
    [:get "/api/search"] (search-handler req)
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "Not found"}))
