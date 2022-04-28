(ns luhmann.log
  (:require
    [luhmann.core :as luhmann])
  (:import
    [ch.qos.logback.classic Level Logger]
    [ch.qos.logback.classic.encoder PatternLayoutEncoder]
    [ch.qos.logback.core.rolling RollingFileAppender TimeBasedRollingPolicy]
    [org.slf4j LoggerFactory]))


(def log (LoggerFactory/getLogger "luhmann"))

(defn info
  [msg & args]
  (.info log msg (into-array args)))


;;============================================================
;; Start/stop
;;

(defn stop
  [])


(defn start
  [_config]
  ;; Props to https://akhikhl.wordpress.com/2013/07/11/programmatic-configuration-of-slf4jlogback/
  (let [context (doto (LoggerFactory/getILoggerFactory)
                  (.reset))

        encoder (doto (PatternLayoutEncoder.)
                  (.setContext context)
                  (.setPattern "%-12date{YYYY-MM-dd HH:mm:ss.SSS} %-5level – %msg%n")
                  (.start))

        appender (doto (RollingFileAppender.)
                   (.setContext context)
                   (.setName "logFile")
                   (.setEncoder encoder)
                   (.setAppend true)
                   (.setFile (str (luhmann/luhmann-dir) "/logs/luhmann.log")))

        rolling-policy (doto (TimeBasedRollingPolicy.)
                        (.setContext context)
                        (.setParent appender)
                        (.setFileNamePattern (str (luhmann/luhmann-dir) "/logs/luhmann-%d{yyyy-MM-dd}.log"))
                        (.setMaxHistory 3)
                        (.start))

        appender (doto appender
                   (.setRollingPolicy rolling-policy)
                   (.start))]

    (doto (.getLogger context Logger/ROOT_LOGGER_NAME)
      (.addAppender appender)
      (.setLevel Level/INFO)
      (.setAdditive false))

    (info "=== Logging started ===")))
