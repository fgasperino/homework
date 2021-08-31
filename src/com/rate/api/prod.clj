(ns com.rate.api.prod
  (:require [com.rate.api.handlers :as handlers]
            [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn- start []
  (run-jetty
   #'handlers/homework-routes
   {:port 8000
    :join? true}))

(defn -main [& args]
  (start))
