(ns com.rate.cmdline
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [com.rate.core :as core])
  (:gen-class))

(def sort-arguments
  {:option-1
   {:fn #(core/list-sorted-favorite-color-last-name {:acending true :re-format true})
    :display "Sort by favorite color, last name (ascending)"}
   :option-2 
   {:fn #(core/list-sorted-date-of-birth {:acending true :re-format true})
    :display "Sort by date-of-birth (ascending)"}
   :option-3 
   {:fn #(core/list-sorted-last-name {:acending false :re-format true})
    :display "Sort by last name (descending)"}})

(defn- consume-file [path]
  (with-open [reader (io/input-stream path)]
    (-> reader
        core/ingest-records)))

(defn- consume-files [paths]
  (map consume-file paths))

(defn- sort-files [sort-fn paths]
  (doall (consume-files paths))
  (sort-fn))

(def cli-options
  [["-f" "--file FILE" "Input File"
    :default []
    :multi true
    :update-fn conj]
   ["-h" "--help"]])

(def sort-arguments-usage 
  (string/join
   \newline
   (map
    (fn [x]
      (str "  " (name (key x)) ": " (:display (val x))))
    sort-arguments)))
  
(defn- usage [options-summary]
  (->> ["GRTech Interview Homework - Franco Gasperino."
        ""
        "Usage: homework [options] <sort argument>"
        ""
        "Options:"
        options-summary
        ""
        "Sort argument:"
        sort-arguments-usage]
       (string/join \newline)))

(defn- error-message [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join newline errors)))

(defn- validate-args
  [args]
  (let [{:keys [arguments options errors summary]} (parse-opts args cli-options)
        argument (first arguments)
        sort-fn (get-in sort-arguments [(keyword argument) :fn])]
    (cond
      (and (empty? arguments) (nil? sort-fn) true)
        {:errors [(str "Unknown sort argument \"" argument "\"")]
         :summary summary}
      (and (some? sort-fn)
           (seq (:file options))
           (nil? errors)
           true)
        {:sort-fn sort-fn
         :files (:file options)
         :summary summary}
      (some? errors)
        {:errors errors :summary summary}
      :default
        {:summary summary})))

(defn- exit [status message]
  (println message)
  (System/exit status))

(defn -main [& args]
  (let [{:keys [sort-fn files errors summary]} (validate-args args)]
    (cond
      (and (some? sort-fn) (some? files) true)
      (exit 0 (sort-files sort-fn files))
      (some? errors)
      (exit 1 (string/join
               (concat
                (error-message errors)
                (usage summary))))
      :default
      (exit 1 (usage summary)))))

(comment

 ;; Reset the local namespace bindings.
  (map #(ns-unmap *ns* %) (keys (ns-map *ns*)))
  
,)