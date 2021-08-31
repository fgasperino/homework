(ns com.rate.core
  (:require [clojure.string :as string]
            [clojure.spec.alpha :as spec]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [tick.core :as tick]))

;;
;; State management for managed records.
;;

;; Atom persisting valid records.
(defonce record-store (atom []))

(defn- append-records
  "Concat a sequence of records to the persistent store.
   Attempts to de-duplicate records before persisting.
   Returns the new collection."
  [coll]
  (swap! record-store #(distinct (concat %1 %2)) coll))

;;
;; CSV Handling.
;;

(def field-order
  [:last-name
   :first-name
   :email-address
   :favorite-color
   :date-of-birth])

(defn- build-records
  "Return a sequence of maps by using the keyword sequence
   in the binding field-order paired with the vector index
   of each input element.
   
   -----
   
   (build-records 
    [[\"Smith\" \"Bob\" \"bob.smith@rate.com\" \"blue\" \"01/01/1950\"]
     [\"Jones\" \"Tom\" \"tom.jones@rate.com\" \"white\" \"02/02/1961\"]]) 
   
    => ({:last-name \"Smith\"
         :first-name \"Bob\"
         :email-address \"bob.smith@rate.com\"
         :favorite-color \"blue\"
         :date-of-birth \"01/01/1950\"}
        {:last-name \"Jones\"
         :first-name \"Tom\"
         :email-address \"tom.jones@rate.com\"
         :favorite-color \"white\"
         :date-of-birth \"02/02/1961\"})
   "
  [coll]
  (map zipmap (repeat field-order) coll))

(defn- normalize-delimiters
  "Return string content with all delimiter options 
   normalized to a comma.
  -----
  (normalize-delimiters \"f1,f2,f3,f4,f5\")
   => \"f1,f2,f3,f4,f5\"

  (normalize-delimiters \"f1,f2 f3  f4|f5\")
   => \"f1,f2,f3,f4,f5\"
   "
  [content]
  (string/replace content #"[\t \|]+" ","))

(defmulti consume 
  "Returns a string read from string or InputStream source.
   
  -----
  (def content \"Smith,Bob,bob.smith@rate.com,blue,01/01/1950\")
  (def stream (io/reader (char-array source)))

  (consume content)
  => \"Smith,Bob,bob.smith@rate.com,blue,01/01/1950\"
   
  (consume stream)
  => \"Smith,Bob,bob.smith@rate.com,blue,01/01/1950\"
  "
  (fn [source] 
    (if (string? source) 
      :as-string 
      :as-stream)))

(defmethod consume :as-string
  [source]
  (with-open [reader (io/reader (char-array source))]
    (consume reader)))

(defmethod consume :default
  [source]
  (slurp source))

(defn- parse-csv
  "Parse a CSV source into a sequence of maps.

   -----
   (def content [\"Smith,Bob,bob.smith@rate.com,blue,01/01/1950\"
                 \"Jones,Tom,tom.jones@rate.com,white,02/02/1961\"])

   (parse-csv (first content))   
    => ({:last-name \"Smith\"
         :first-name \"Bob\"
         :email-address \"bob.smith@rate.com\"
         :favorite-color \"blue\"
         :date-of-birth \"01/01/1950\"})
   
   (parse-csv (string/join \"\\n\" content))
    => ({:last-name \"Smith\",
         :first-name \"Bob\",
         :email-address \"bob.smith@rate.com\",
         :favorite-color \"blue\",
         :date-of-birth \"01/01/1950\"}
        {:last-name \"Jones\",
         :first-name \"Tom\",
         :email-address \"tom.jones@rate.com\",
         :favorite-color \"white\",
         :date-of-birth \"02/02/1961\"}
   "
  [source]
  (-> source
      consume
      normalize-delimiters
      csv/read-csv
      build-records))

(defn- as-tick-date
  "Return a tick date value for parsed raw MM/DD/YYYY field x, or
   nil if invalid.
   -----
  (as-tick-date nil)
   => nil
  (as-tick-date \"\")
   => nil
  (as-tick-date \"00/00/0000\")
   => nil                         
  (as-tick-date \"12/31/2021\")
   => #time/date \"2021-12-31\"
   "
  [x]
  (if (string? x)
    (try
      (let [parts (map #(Integer/parseInt % 10) (string/split x #"/"))]
        (tick/new-date (nth parts 2) (nth parts 0) (nth parts 1)))
      (catch Exception _
        nil))
    nil))

(defn- convert-dates 
  "Return a sequence of maps from coll with each 
   :date-of-birth value replaced the value returned
   from as-tick-date.

  -----
  (def records '({:last-name \"Smith\"
                  :first-name \"Bob\"
                  :email-address \"bob.smith@rate.com\"
                  :favorite-color \"blue\"
                  :date-of-birth \"01/01/1950\"}
                 {:last-name \"Jones\"
                  :first-name \"Tom\"
                  :email-address \"tom.jones@rate.com\"
                  :favorite-color \"white\"
                  :date-of-birth \"02/02/1961\"}))

  (convert-dates records)
  => ({:last-name \"Smith\",
       :first-name \"Bob\",
       :email-address \"bob.smith@rate.com\",
       :favorite-color \"blue\",
       :date-of-birth #time/date \"1950-01-01\"}
      {:last-name \"Jones\",
       :first-name \"Tom\",
       :email-address \"tom.jones@rate.com\",
       :favorite-color \"white\",
       :date-of-birth #time/date \"1961-02-02\"})
  "
  [coll]
  (map 
   (fn [m] 
     (assoc m :date-of-birth (as-tick-date (:date-of-birth m)))) 
   coll))

;; Regex Patterns for Spec
(def name-field-pattern 
  (re-pattern #"[a-zA-Z0-9\-]+"))
(def email-field-pattern 
  (re-pattern #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z0-9\-]{2,63}$"))

;; Spec definition for a name value.
(spec/def ::name?
  (spec/and
   string?
   #(re-matches name-field-pattern %)))

;; Spec definition for an email value.
(spec/def ::email?
  (spec/and
   string?
   #(re-matches email-field-pattern %)))

;; Spec definition for a date-of-birth value. The
;; MM/DD/YYYY string must be convertable to a Tick
;; date.
(spec/def ::date-of-birth? tick/date?)

;; Spec definitions to allow key conforming.
(spec/def ::last-name ::name?)
(spec/def ::first-name ::name?)
(spec/def ::email-address ::email?)
(spec/def ::favorite-color ::name?)
(spec/def ::date-of-birth ::date-of-birth?)

;; Spec definition for the finalized, parsed 
;; input record.
(spec/def ::valid-record?
  (spec/and
   (spec/map-of
    keyword? some?
    :conform-keys true)
   (spec/keys
    :req-un
    [::last-name
     ::first-name
     ::email-address
     ::favorite-color
     ::date-of-birth])))

(defn- conform-record
  "Conform input record x to the spec ::valid-record?
   definition. Returns the conformed value on success, 
   nil on failure.
   
   -----
  (def m {:last-name \"Smith\" 
          :first-name \"Bob\" 
          :email-address \"bob@rate.com\"
          :favorite-color \"green\"
          :date-of-birth (as-tick-date \"01/01/1970\")})
   
  (conform-record m)
    => {:last-name \"Smith\",
        :first-name \"Bob\",
        :email-address \"bob@rate.com\",
        :favorite-color \"green\",
        :date-of-birth #time/date \"1970-01-01\"}
   
  (conform-record (dissoc m :first-name))
   => nil
  (conform-record (assoc m :first-name 0))
   => nil
  (conform-record (assoc m :date-of-birth \"00/00/0000\"))
   => nil
   "
  [coll]
  (let [c (spec/conform ::valid-record? coll)]
    (if (= c ::spec/invalid)
      (do (tap> (spec/explain ::valid-record? coll)) nil)
      c)))

(defn- conform-records 
  "Return a sequence of records from coll which pass
   spec validation (see: conform-record).
   
   -----
   (def records [{:last-name \"Smith\"
                  :first-name \"Bob\"
                  :email-address \"bob.smith@rate.com\"
                  :favorite-color \"blue\"
                  :date-of-birth \"bad-DOB!\"}
                 {:last-name \"Jones\"
                  :first-name \"Tom\"
                  :email-address \"tom.jones@rate.com\"
                  :favorite-color \"white\"
                  :date-of-birth #time/date \"1974-01-01\"}])
   
   (conform-records records)
   => ({:last-name \"Jones\",
        :first-name \"Tom\",
        :email-address \"tom.jones@rate.com\",
        :favorite-color \"white\",
        :date-of-birth #time/date \"1974-01-01\"})

   ;; OPTIONAL - Attach a tap to observe rejected records.
   (add-tap println)
   > => \"bad-DOB!\" - failed: date? in: [:date-of-birth] at: [:date-of-birth] spec: :com.rate.core/date-of-birth?
   "
  [coll]
  (filter #(some? (conform-record %)) coll))

;;
;; Functions exposed to APIs for access.
;;

(defn parse-records
  "Consume and parse a sequence of raw input records, returns 
   a sequence of all valid records.
  -----
   
  (def records [\"Smith,Bob,bob@rate.com,green,01/01/1970\"
                \"Jones Tom tom@rate.com blue 02/02/1971\"
                \"Jacobs|Jake|jake@rate.com|red|03/03/1972\"
                \"Apple|Bad|brokenemail|black|03/03/1972\"])

  (parse-records (string/join \"\\n\" (take 2 records)))
   => ({:last-name \"Smith\",
        :first-name \"Bob\",
        :email-address \"bob@rate.com\",
        :favorite-color \"green\",
        :date-of-birth #time/date \"1970-01-01\"}
       {:last-name \"Jones\",
        :first-name \"Tom\",
        :email-address \"tom@rate.com\",
        :favorite-color \"blue\",
        :date-of-birth #time/date \"1971-02-02\"})
                                        
  (parse-records (string/join \"\\n\" (drop 2 records)))
   => ({:last-name \"Jacobs\",
        :first-name \"Jake\",
        :email-address \"jake@rate.com\",
        :favorite-color \"red\",
        :date-of-birth #time/date \"1972-03-03\"})
  "
  ;; Tested by com.rate.core-test/parse-records-test
  [source]
  (-> source
      parse-csv
      convert-dates
      conform-records))

(defn ingest-records
  "Consume and parse a sequence of raw input records, persisting
   each record to the in-memory store. Returns a sequence of all
   persisted records.

   Records which do not pass spec validation will not be persisted
   or returned.
  
  ----- 
  (def records [\"Smith,Bob,bob@rate.com,green,01/01/1970\"
                \"Jones Tom tom@rate.com blue 02/02/1971\"
                \"Jacobs|Jake|jake@rate.com|red|03/03/1972\"
                \"Apple|Bad|brokenemail|black|03/03/1972\"])

  (ingest-records (string/join \"\\n\" (take 2 records)))
   => ({:last-name \"Smith\",
        :first-name \"Bob\",
        :email-address \"bob@rate.com\",
        :favorite-color \"green\",
        :date-of-birth #time/date \"1970-01-01\"}
       {:last-name \"Jones\",
        :first-name \"Tom\",
        :email-address \"tom@rate.com\",
        :favorite-color \"blue\",
        :date-of-birth #time/date \"1971-02-02\"})
                                        
  (ingest-records (string/join \"\\n\" (drop 2 records)))
   => ({:last-name \"Jacobs\",
        :first-name \"Jake\",
        :email-address \"jake@rate.com\",
        :favorite-color \"red\",
        :date-of-birth #time/date \"1972-03-03\"})

  ;; NOTE: internal detail, here for illustration:
  @record-store
   => ({:last-name \"Smith\",
        :first-name \"Bob\",
        :email-address \"bob@rate.com\",
        :favorite-color \"green\",
        :date-of-birth #time/date \"1970-01-01\"}
       {:last-name \"Jacobs\",
        :first-name \"Jake\",
        :email-address \"jake@rate.com\",
        :favorite-color \"blue\",
        :date-of-birth #time/date \"1977-02-02\"}
       {:last-name \"Jones\",
        :first-name \"Tom\",
        :email-address \"tom@rate.com\",
        :favorite-color \"gray\",
        :date-of-birth #time/date \"1950-03-03\"})
  "
  ;; Tested by com.rate.core-test/ingest-records-test
  [source]
  (-> source
      parse-records
      append-records))

(defn list-records
  "Return the persisted record collection."
  []
  @record-store)

(defn list-sorted
  "Return the persisted record collection, sorted by
   the parsed function."
  [func]
  (sort-by func (list-records)))

;;
;; Homework - Preset Output Ordering.
;;

(defn original-format
  "Return a parsed record to the original CSV/delimited format.
   
   ----- 
  (def record
    {:last-name \"Smith\" 
     :first-name \"Bob\" 
     :email-address \"bob@rate.com\" 
     :favorite-color \"blue\"
     :date-of-birth #time/date \"1950-01-03\"})

   (original-format record)
    => \"Smith Bob bob@rate.com blue 01/03/1950\n\"
   "
  ;; Tested by com.rate.core-test/original-format-test
  [x]
  (str (:last-name x)
       " "
       (:first-name x)
       " "
       (:email-address x)
       " "
       (:favorite-color x)
       " "
       (tick/format
        (tick/formatter "MM/dd/YYYY")
        (:date-of-birth x))))

(defn apply-formatting
  "Apply original record formatting (CSV) to coll when original? is true.
   
   -----
  (def records
     [{:last-name \"Smith\"
       :first-name \"Bob\"
       :email-address \"bob @rate.com\"
       :favorite-color \"blue\"
       :date-of-birth #time/date \"1950-01-03\"}
      {:last-name \"Jones\"
       :first-name \"Tom\"
       :email-address \"tom @rate.com\"
       :favorite-color \"gray\"
       :date-of-birth #time/date \"1950-03-03\"}])
  
  (apply-formatting false records)
   => [{:last-name \"Smith\",
        :first-name \"Bob\",
        :email-address \"bob@rate.com\",
        :favorite-color \"blue\",
        :date-of-birth #time/date \"1950-01-03\"}
       {:last-name \"Jones\",
        :first-name \"Tom\",
        :email-address \"tom@rate.com\",
        :favorite-color \"gray\",
        :date-of-birth #time/date \"1950-03-03\"}]
       
  (apply-formatting true records)
   => \"Smith Bob bob @rate.com blue 01/03/1950\nJones Tom tom @rate.com gray 03/03/1950\"
   "
  ;; Tested by com.rate.core-test/apply-formatting-test
  [original? coll]
  (if original?
    (string/join "\n" (map original-format coll))
    coll))

(defn list-sorted-by
  "Returns an sorted collection of the current persisted
   records.

   Sorted by applying sort-fn to list-sorted. Should the
   ascending key be false, the sort order is reversed.

   Optional re-formatting to CSV can be enabled by setting
   the re-format key to true.
   "
  [{:keys [sort-fn ascending re-format] :or {ascending true}}]
  (let [ordering #(if ascending % (reverse %))
        formatting #(apply-formatting re-format %)]
    (-> sort-fn
        list-sorted
        ordering
        formatting)))

(defn list-sorted-favorite-color-last-name
  "Output 1 - sort by favorite color, followed by last name."
  ;; Tested by com.rate.core-test/list-sorted-favorite-color-last-name-test
  ([]
   (list-sorted-favorite-color-last-name {}))
  ([kwargs]
   (list-sorted-by
    (assoc kwargs :sort-fn (juxt :favorite-color :last-name)))))

(defn list-sorted-date-of-birth
  "Output 2 - sort by date-of-birth, ascending."
  ;; Tested by com.rate.core-test/list-sorted-date-of-birth-test
  ([]
   (list-sorted-date-of-birth {}))
  ([kwargs]
   (list-sorted-by
    (assoc kwargs :sort-fn :date-of-birth))))

(defn list-sorted-last-name
  "Output 3 - sort by last name."
  ;; Tested by com.rate.core-test/list-sorted-last-name-test
  ([]
   (list-sorted-last-name {}))
  ([kwargs]
   (list-sorted-by
    (merge kwargs {:sort-fn :last-name}))))

(comment
 ;; Reset the local namespace bindings.
  (map #(ns-unmap *ns* %) (keys (ns-map *ns*)))
)