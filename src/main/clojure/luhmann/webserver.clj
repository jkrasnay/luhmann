(ns luhmann.webserver
  (:require
    [babashka.fs :as fs]
    [clojure.string :as string]
    [hiccup2.core :as hiccup]
    [luhmann.api :as api]
    [luhmann.core :as luhmann]
    [luhmann.lucene :as lucene]
    [luhmann.watcher :as watcher]
    [org.httpkit.server :refer [as-channel run-server send!]]
    [ring.middleware.file :refer [wrap-file]]
    [ring.middleware.resource :refer [wrap-resource]]
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
                               [:div.searchResult
                                [:a {:href (:path result)} (:title result)]
                                [:div (hiccup/raw (:summary-html result))]]))
                       [:div "No results"])]]]))}))


;;============================================================
;; Chrome
;;

(def luhmann-chrome-html
  (str (hiccup/html [:div.luh-outer
                     [:div.luh-header
                      [:a {:href "/"}
                       [:svg.luh-logo {:xmlns "http://www.w3.org/2000/svg"
                                       :viewBox "0 0 24 24"}
                        [:path {:d "M20 3H4a2 2 0 0 0-2 2v2a2 2 0 0 0 1 1.72V19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8.72A2 2 0 0 0 22 7V5a2 2 0 0 0-2-2zM4 5h16v2H4zm1 14V9h14v10z"}]
                        [:path {:d "M8 11h8v2H8z"}]]]
                      [:div.luh-brand
                       [:a {:href "/"} "Luhmann"]]
                      [:div.luh-spacer]
                      [:div.luh-search
                       [:form {:method "GET"
                               :action "/search"}
                        [:input {:name "q"}]]
                       [:svg {:xmlns "http://www.w3.org/2000/svg"
                              :viewBox "0 0 24 24"}
                        [:path {:d "M10 18a7.952 7.952 0 0 0 4.897-1.688l4.396 4.396 1.414-1.414-4.396-4.396A7.952 7.952 0 0 0 18 10c0-4.411-3.589-8-8-8s-8 3.589-8 8 3.589 8 8 8zm0-14c3.309 0 6 2.691 6 6s-2.691 6-6 6-6-2.691-6-6 2.691-6 6-6z"}]]]
                      [:input#luh-copy-input]]
                     [:div.luh-body
                      [:div.luh-inner]]])))




(defn wrap-chrome*
  [handler req]
  (let [resp (handler req)
        body (:body resp)]
    (if (and (= 200 (:status resp))
             (= "text/html" (content-type resp)))
      (if-let [doc (cond
                     (instance? File body) (Jsoup/parse ^File body "utf-8")
                     (instance? String body) (Jsoup/parse ^String body))]
        (let [body-children (-> doc .body .children)]
          (-> doc .head (.append "<link rel='stylesheet' href='/luhmann.css'>"))
          (-> doc .head (.append "<link rel='preconnect' href='https://fonts.googleapis.com'>"))
          (-> doc .head (.append "<link href='https://fonts.googleapis.com/css2?family=Source+Serif+4:ital,wght@0,400;0,500;1,400;1,500&display=swap' rel='stylesheet'>"))
          ;(-> doc .body (.prepend banner-html))
          (-> doc .body (.prepend luhmann-chrome-html))
          (let [luh-inner (.selectFirst doc ".luh-inner")]
            (.appendChildren luh-inner body-children))
          (-> doc .head (.append "<script src='/luhmann.js'></script>"))
          (assoc resp :body (.outerHtml doc)))
        resp)
      resp)))


(defn wrap-chrome
  "Middleware that injects 'chrome' (additional elements around the main
  content) in returned text.
  "
  [handler]
  (fn [req]
    (wrap-chrome* handler req)))


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

    (string/starts-with? (:uri req) "/api")
    (api/api-handler req)

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
  (reset! server (run-server (-> #'handler
                                 (wrap-file (luhmann/site-dir))
                                 (wrap-resource "public")
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
               (fs/starts-with? (fs/path (luhmann/root-dir) path) (luhmann/site-dir)))
      (reload-browser))))
