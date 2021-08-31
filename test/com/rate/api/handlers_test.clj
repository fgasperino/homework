(ns com.rate.api.handlers-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing run-tests]]
            [ring.mock.request :as mock]
            [com.rate.api.handlers :as handlers]
            [com.rate.core :as core]))

(def valid-test-data
  ["Smith Bob bob@rate.com black 06/20/1975"
   "Jacobs,Jake,jake@rate.com,blue,01/30/1971"
   "Musk Elon elon@rate.com green 12/30/1973"
   "Jones|Tom|tom@rate.com|green|10/01/1956"])

(defn- prepare-records [coll]
  (string/join "\n" coll))

(defn- stage-records [coll]
  (core/ingest-records (prepare-records coll)))

(def expected-headers {"Content-Type" "text/csv"})

(defn- build-test [handler-fn core-fn]
  (fn [{:keys [status headers test-data] :or {status 200 headers expected-headers}}]
    ;; Reset internal state.
    (reset! core/record-store [])

    (when test-data
      ;; Bootstrap test data.
      (stage-records test-data))

    (let [request (mock/request :get "/")
          expected (core-fn)
          returned (handler-fn request)]
      (is (= {:status status
              :headers headers
              :body expected} 
             returned)))))
  
(deftest list-by-favorite-color-test
  (let [test-fn (build-test
                 handlers/list-by-favorite-color
                 #(core/list-sorted-favorite-color-last-name {:re-format true}))]
    (testing "list-by-favorite-color (no records)."
      (test-fn {}))
    (testing "list-by-favorite-color (records)."
      (test-fn {:test-data valid-test-data}))))

(deftest list-by-birth-date-test
  (let [test-fn (build-test
                 handlers/list-by-birth-date
                 #(core/list-sorted-date-of-birth {:re-format true}))]
    (testing "list-by-birth-date (no records)."
      (test-fn {}))
    (testing "list-by-birth-date (records)."
      (test-fn {:test-data valid-test-data}))))

(deftest list-by-last-name-test
  (let [test-fn (build-test
                 handlers/list-by-last-name
                 #(core/list-sorted-last-name {:re-format true :ascending false}))]
    (testing "list-by-last-name (no records)."
      (test-fn {}))
    (testing "list-by-last-name (records)."
      (test-fn {:test-data valid-test-data}))))

(run-tests)