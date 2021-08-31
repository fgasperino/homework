(ns com.rate.api.handlers
  (:require [com.rate.core :as core]
            [ring.middleware.params :refer [wrap-params]]
            [compojure.core :refer [routes GET POST]]
            [compojure.route :refer [not-found]]))

(def default-headers 
  {"Content-Type" "text/csv"})

(defn list-by-favorite-color [request]
  {:status 200
   :headers default-headers
   :body (core/list-sorted-favorite-color-last-name
          {:ascending true :re-format true})})

(defn list-by-birth-date [request]
  {:status 200
   :headers default-headers
   :body (core/list-sorted-date-of-birth
          {:ascending true :re-format true})})

(defn list-by-last-name [request]
  {:status 200
   :headers default-headers
   :body (core/list-sorted-last-name
          {:ascending false :re-format true})})

(defn ingest-record [request]
  {:status 201
   :headers default-headers
   :body (core/ingest-records (:body request))})

(def homework-routes
  (routes
   (GET "/records/color" [] (wrap-params list-by-favorite-color))
   (GET "/records/birthdate" [] (wrap-params list-by-birth-date))
   (GET "/records/name" [] (wrap-params list-by-last-name))
   (POST "/records" [] (wrap-params ingest-record))
   (not-found "Page Not Found")))
