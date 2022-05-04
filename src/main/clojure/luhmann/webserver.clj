(ns luhmann.webserver
  (:require
    [clojure.string :as string]
    [hiccup2.core :as hiccup]
    [luhmann.core :as luhmann]
    [luhmann.lucene :as lucene]
    [luhmann.watcher :as watcher]
    [org.httpkit.server :refer [as-channel run-server send!]]
    [ring.middleware.file :refer [wrap-file]]
    [ring.middleware.params :refer [wrap-params]]
    ))


(defonce server (atom nil))

(defonce channels (atom #{}))


(defn search-handler
  [req]
  (let [q (get-in req [:query-params "q"])
        results (when-not (string/blank? q)
                  (lucene/search q))]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body (str (hiccup/html
                  [:html
                   [:body
                    [:h1 (str "Search results: '" q "'")]
                    (if (seq results)
                      (into [:div]
                            (for [result results]
                              [:div
                               [:a {:href (:path result)} (:title result)]]))
                      [:div "No results"])]]))}))


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
  (reset! server (run-server (-> handler
                                 (wrap-file (luhmann/site-dir))
                                 (wrap-params))
                             {:port (:port config)})))


;;============================================================
;; Registrations
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
