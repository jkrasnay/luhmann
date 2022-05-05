(ns luhmann.webserver
  (:require
    [babashka.fs :as fs]
    [clojure.java.io :as io]
    [clojure.string :as string]
    [hiccup2.core :as hiccup]
    [luhmann.core :as luhmann]
    [luhmann.lucene :as lucene]
    [luhmann.watcher :as watcher]
    [org.httpkit.server :refer [as-channel run-server send!]]
    [ring.middleware.file :refer [wrap-file]]
    [ring.middleware.params :refer [wrap-params]])
  (:import
    [java.io File]
    [org.jsoup Jsoup]))


(defonce server (atom nil))

(defonce channels (atom #{}))


;;============================================================
;; Utilities
;;


(def ext->content-type
  {"html" "text/html"})


(defn file-extension
  [file]
  (let [filename (str file)
        i (string/last-index-of filename ".")]
    (-> filename
        (subs (inc i))
        (string/lower-case))))


(defn content-type
  "Guess content type of the response. `wrap-file` does not set the
  `Content-Type` header so if we get a File we have to infer the type from the file extension.
  "
  [resp]
  (or (get-in resp [:headers "Content-Type"])
      (when (instance? File (:body resp))
        (ext->content-type (file-extension (:body resp))))))


;;============================================================
;; Search
;;

(defn search-handler
  [req]
  (let [q (get-in req [:query-params "q"])
        results (when-not (string/blank? q)
                  (lucene/search q))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str (hiccup/html
                  [:html
                   [:head
                    [:meta {:charset "UTF-8"}]
                    [:meta {:http-equiv "X-UA-Compatible"
                            :content "IE=edge"}]
                    [:meta {:name "viewport"
                            :content "width=device-width, initial-scale=1.0"}]
                    [:title "Search"]]
                   [:body
                    [:div#header
                     [:h1 (str "Search results: '" q "'")]]
                    [:div#content
                     (if (seq results)
                       (into [:div]
                             (for [result results]
                               [:div
                                [:a {:href (:path result)} (:title result)]]))
                       [:div "No results"])]]]))}))


;;============================================================
;; Chrome
;;

(def banner-html
  (str (hiccup/html [:div {:class "banner"}
                     [:div {:class "logo"}
                      [:a {:href "/"} "Luhmann"]]
                     [:div [:form {:method "GET"
                                   :action "/search"}
                            [:input {:name "q"}]]]])))


(defn wrap-chrome*
  [handler req]
  (let [resp (handler req)
        body (:body resp)]
    (println "------------------------")
    (println "uri" (:uri req))
    (println "status" (:status resp))
    (println "content-type" (content-type resp))
    (if (and (= 200 (:status resp))
             (= "text/html" (content-type resp)))
      (if-let [doc (cond
                     (instance? File body) (Jsoup/parse ^File body "utf-8")
                     (instance? String body) (Jsoup/parse ^String body))]
        (do
          (println "munging" (:uri req))
          (-> doc .head (.append "<link rel='stylesheet' href='/luhmann.css'>"))
          (-> doc .body (.prepend banner-html))
          (assoc resp :body (.outerHtml doc)))
        (do (println "not munging 1") resp))
      (do (println "not munging 2") resp))))


(defn wrap-chrome
  "Middleware that injects 'chrome' (additional elements around the main
  content) in returned text.
  "
  [handler]
  (fn [req]
    (wrap-chrome* handler req)))


;;============================================================
;; Resources
;;

(def public-resources
  ["luhmann.css"])

(defn copy-resources
  []
  (doseq [resource public-resources]
    (let [dest (fs/file (luhmann/site-dir) resource)]
      (fs/create-dirs (fs/parent dest))
      (io/copy (io/input-stream (io/resource (str "public/" resource))) dest))))

#_(copy-resources)


;;============================================================
;; Web Server
;;

(defn handler
  [req]
  (cond

    (= "/ws" (:uri req))
    (as-channel req {:on-open (fn [ch]
                                (swap! channels conj ch))
                     :on-close (fn [ch _]
                                 (swap! channels disj ch))})

    (= "/search" (:uri req))
    (search-handler req)

    :else
    {:status 404
     :headers {"Content-Type" "text/plain"}
     :body "Not found"}))


(defn stop
  []
  (when @server
    (@server)
    (reset! server nil)))

(defn start
  [config]
  (stop)
  (copy-resources)
  (reset! server (run-server (-> #'handler
                                 (wrap-file (luhmann/site-dir))
                                 (wrap-chrome)
                                 (wrap-params))
                             {:port (:port config)})))


;;============================================================
;; Web Sockets / Reload
;;

(defn reload-browser
  []
  (doseq [ch @channels]
    (send! ch "reload")))

(watcher/reg-listener
  :webserver
  (fn [{:keys [event path]}]
    (when (and (#{:create :modify} event)
               (luhmann/site-path? path))
      (reload-browser))))
