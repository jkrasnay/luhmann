(ns luhmann.api
  (:require
    [cheshire.core :as cheshire]
    [clojure.string :as string]
    [luhmann.core :as luhmann]
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
  (println "api-handler")
  (condp = [(:request-method req) (:uri req)]
    [:get "/api/config"] (config-handler req)
    [:get "/api/search"] (search-handler req)
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "Not found"}))
