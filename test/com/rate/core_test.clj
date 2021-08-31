(ns com.rate.core-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is testing run-tests]]
            [com.rate.core]))

;; Test data which should be successfully parsed, 
;; conformed and made available for query.
(def valid-test-data
  ["Smith Bob bob@rate.com black 06/20/1975"
   "Jacobs,Jake,jake@rate.com,blue,01/30/1971"
   "Musk Elon elon@rate.com green 12/30/1973"
   "Jones|Tom|tom@rate.com|green|10/01/1956"])

;; Test data which contains content which should
;; fail CSV parsing or spec validation.
(def invalid-test-data
  ["Smith+Bob+bob1@rate.com+black+06/20/1975"
   "Smith|Bob|bob2@rate.com|black|Jun 20 1975"
   "Smith|Bob|invalid|black|06/20/1975"
   "Smith Bob bob3@rate.com black 13/32/0000"])

(defn- join-test-data [coll]
  (string/join "\n" coll))

(deftest parse-records-test
  (let [func #(com.rate.core/parse-records %)]
    (testing "Testing valid record content."
      (let [content (join-test-data (take 2 valid-test-data))]
        (is (=
             [{:last-name "Smith"
               :first-name "Bob"
               :email-address "bob@rate.com"
               :favorite-color "black"
               :date-of-birth #time/date "1975-06-20"}
              {:last-name "Jacobs"
               :first-name "Jake"
               :email-address "jake@rate.com"
               :favorite-color "blue"
               :date-of-birth #time/date "1971-01-30"}]
             (func content))))
    (testing "Testing non-conforming record content."
      (let [content (join-test-data invalid-test-data)]
        (is (= [] (func content))))))))

(deftest ingest-records-test
  (let [func #(com.rate.core/ingest-records %)
        store com.rate.core/record-store]
    (testing "Testing bulk record ingestion."
      (reset! store [])
      (let [content (join-test-data valid-test-data)]
        (is (= (count (func content)) (count @store)))))
    (testing "Testing incremental record ingestion."
      (reset! store [])
      (let [chunk-1 (take 2 valid-test-data)
            chunk-2 (drop 2 valid-test-data)]
        (func (join-test-data chunk-1))
        (is (= (count chunk-1) (count @store)))
        (func (join-test-data chunk-2))
        (is (= (+ (count chunk-1) (count chunk-2)) (count @store)))))))

(deftest list-sorted-test
  (let [func #(com.rate.core/list-sorted %)
        store com.rate.core/record-store
        records (join-test-data valid-test-data)]
    ;; Bootstrap test data.
    (reset! store [])
    (com.rate.core/ingest-records records)

    (testing "Testing list sorted by :first-name."
      (is (= ["Bob" "Elon" "Jake" "Tom"]
             (into [] (map #(:first-name %) (func :first-name))))))
    (testing "Testing list sorted by :last-name, then :first-name."
      (is (= ["Jacobs,Jake" "Jones,Tom" "Musk,Elon" "Smith,Bob"]
             (into [] 
                   (map #(str (:last-name %) "," (:first-name %)) 
                        (func (juxt :last-name :first-name)))))))))
 
(deftest orginal-format-test
  (let [func #(com.rate.core/original-format %)
        raw (first valid-test-data)
        parsed (first (com.rate.core/parse-records raw))]
    (testing "Testing expected format."
      (is (= raw (func parsed))))))

(deftest apply-formatting-test
  (let [func #(com.rate.core/apply-formatting %1 %2)
        raw (join-test-data (take 2 valid-test-data))
        parsed (com.rate.core/parse-records raw)]
    (testing "Testing no-formatting option."
      (is (= parsed (func false parsed))))
    (testing "Testing original formatting option"
      (is (string? (func true parsed))))))

(deftest list-sorted-favorite-color-last-name-test
  (let [func com.rate.core/list-sorted-favorite-color-last-name
        store com.rate.core/record-store]
    ;; Bootstrap test data.
    (reset! store [])
    (com.rate.core/ingest-records (join-test-data valid-test-data))

    (testing "List sorted by favorite color, last name."
      (is (= [{:last-name "Smith"
                :first-name "Bob"
                :email-address "bob@rate.com"
                :favorite-color "black"
                :date-of-birth #time/date "1975-06-20"}
               {:last-name "Jacobs"
                :first-name "Jake"
                :email-address "jake@rate.com"
                :favorite-color "blue"
                :date-of-birth #time/date "1971-01-30"}
               {:last-name "Jones"
                :first-name "Tom"
                :email-address "tom@rate.com"
                :favorite-color "green"
                :date-of-birth #time/date "1956-10-01"}
               {:last-name "Musk"
                :first-name "Elon"
                :email-address "elon@rate.com"
                :favorite-color "green"
                :date-of-birth #time/date "1973-12-30"}]
              (func))))
    (testing "List sorted by favorite color, last name (original format)."
      (is (= (string/join "\n"
                          ["Smith Bob bob@rate.com black 06/20/1975"
                           "Jacobs Jake jake@rate.com blue 01/30/1971"
                           "Jones Tom tom@rate.com green 10/01/1956"
                           "Musk Elon elon@rate.com green 12/30/1974"])
             (func {:re-format true}))))))
  
(deftest list-sorted-date-of-birth-test
  (let [func com.rate.core/list-sorted-date-of-birth
        store com.rate.core/record-store]
    ;; Bootstrap test data.
    (reset! store [])
    (com.rate.core/ingest-records (join-test-data valid-test-data))

    (testing "List sorted by date of birth, ascending."
      (is (= [{:last-name "Jones"
               :first-name "Tom"
               :email-address "tom@rate.com"
               :favorite-color "green"
               :date-of-birth #time/date "1956-10-01"}
              {:last-name "Jacobs"
               :first-name "Jake"
               :email-address "jake@rate.com"
               :favorite-color "blue"
               :date-of-birth #time/date "1971-01-30"}
              {:last-name "Musk"
               :first-name "Elon"
               :email-address "elon@rate.com"
               :favorite-color "green"
               :date-of-birth #time/date "1973-12-30"}
              {:last-name "Smith"
               :first-name "Bob"
               :email-address "bob@rate.com"
               :favorite-color "black"
               :date-of-birth #time/date "1975-06-20"}]
             (func))))
    (testing "List sorted by date of birth, ascending (original format)."
      (is (= (string/join "\n"
                          ["Jones Tom tom@rate.com green 10/01/1956"
                           "Jacobs Jake jake@rate.com blue 01/30/1971"
                           "Musk Elon elon@rate.com green 12/30/1974"
                           "Smith Bob bob@rate.com black 06/20/1975"])
             (func {:re-format true}))))))

(deftest list-sorted-last-name-test
  (let [func com.rate.core/list-sorted-last-name
        store com.rate.core/record-store]
    ;; Bootstrap test data.
    (reset! store [])
    (com.rate.core/ingest-records (join-test-data valid-test-data))

    (testing "List sorted by last name, ascending."
      (is (= [{:last-name "Jacobs"
               :first-name "Jake"
               :email-address "jake@rate.com"
               :favorite-color "blue"
               :date-of-birth #time/date "1971-01-30"}
              {:last-name "Jones"
               :first-name "Tom"
               :email-address "tom@rate.com"
               :favorite-color "green"
               :date-of-birth #time/date "1956-10-01"}
              {:last-name "Musk"
               :first-name "Elon"
               :email-address "elon@rate.com"
               :favorite-color "green"
               :date-of-birth #time/date "1973-12-30"}
              {:last-name "Smith"
               :first-name "Bob"
               :email-address "bob@rate.com"
               :favorite-color "black"
               :date-of-birth #time/date "1975-06-20"}]
             (func {:ascending true}))))
    (testing "List sorted by last name, descending."
      (is (= [{:last-name "Smith"
               :first-name "Bob"
               :email-address "bob@rate.com"
               :favorite-color "black"
               :date-of-birth #time/date "1975-06-20"}
              {:last-name "Musk"
               :first-name "Elon"
               :email-address "elon@rate.com"
               :favorite-color "green"
               :date-of-birth #time/date "1973-12-30"}
              {:last-name "Jones"
               :first-name "Tom"
               :email-address "tom@rate.com"
               :favorite-color "green"
               :date-of-birth #time/date "1956-10-01"}
              {:last-name "Jacobs"
               :first-name "Jake"
               :email-address "jake@rate.com"
               :favorite-color "blue"
               :date-of-birth #time/date "1971-01-30"}]
             (func {:ascending false}))))
    (testing "List sorted by last name, ascending (original format)."
      (is (= (string/join "\n"
                          ["Jacobs Jake jake@rate.com blue 01/30/1971"
                           "Jones Tom tom@rate.com green 10/01/1956"
                           "Musk Elon elon@rate.com green 12/30/1974"
                           "Smith Bob bob@rate.com black 06/20/1975"])
             (func {:re-format true}))))
    (testing "List sorted by last name, descending (original format)."
      (is (= (string/join "\n"
                          ["Smith Bob bob@rate.com black 06/20/1975"
                           "Musk Elon elon@rate.com green 12/30/1974"
                           "Jones Tom tom@rate.com green 10/01/1956"
                           "Jacobs Jake jake@rate.com blue 01/30/1971"])
             (func {:ascending false :re-format true}))))))

(run-tests)

(comment
  ;; Reset the local namespace bindings.
  (map #(ns-unmap *ns* %) (keys (ns-map *ns*)))
)