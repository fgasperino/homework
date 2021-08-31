(ns com.rate.api.dev
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.reload :refer [wrap-reload]]
            [com.rate.api.handlers :as handlers])
  (:gen-class))

(def hot-reload 
  (wrap-reload #'handlers/homework-routes))

(defn- start []
  (run-jetty 
   hot-reload
   {:port 8000 
    :join? false}))

(defn -main [& _]
  (start))
