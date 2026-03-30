(ns usage-insights.merge-checkpoint
  (:require [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.time Instant]
           [java.time.temporal IsoFields]))

(defn iso-week
  "Return ISO week string (e.g. '2026-W10') for a given ISO-8601 instant string."
  [start-time]
  (let [instant (Instant/parse start-time)
        zdt     (.atZone instant java.time.ZoneOffset/UTC)
        week    (.get zdt IsoFields/WEEK_OF_WEEK_BASED_YEAR)
        year    (.get zdt IsoFields/WEEK_BASED_YEAR)]
    (format "%d-W%02d" year week)))

(defn add-counts
  "Merge two count maps by summing numeric values for each key."
  [target source]
  (reduce (fn [acc [k v]] (update acc k (fnil + 0) v)) target source))

(defn merge-facet-into-bucket
  "Accumulate one facet's data into a weekly bucket map. Increments session_count, merges friction_counts and goal_categories, tallies outcomes and satisfaction."
  [bucket facet]
  (-> bucket
      (update :session_count (fnil inc 0))
      (update :friction_counts #(add-counts (or % {}) (:friction_counts facet {})))
      (update :goal_categories #(add-counts (or % {}) (:goal_categories facet {})))
      (update-in [:outcomes (keyword (:outcome facet))] (fnil inc 0))
      (#(reduce (fn [b [k v]] (update-in b [:satisfaction k] (fnil + 0) v))
                % (:user_satisfaction_counts facet {})))))

(defn- load-checkpoint [path]
  (let [f (io/file path)]
    (if (.exists f)
      (json/parse-string (slurp f) true)
      {:version 1 :last_updated "" :analyzed_session_ids []
       :config_snapshot {} :weekly_buckets {}})))

(defn merge-into-checkpoint
  "Read checkpoint at path, merge new-session-ids, write result back. Returns updated checkpoint."
  [checkpoint-path session-meta-dir facets-dir new-session-ids config-snapshot]
  (let [checkpoint   (load-checkpoint checkpoint-path)
        analyzed-ids (set (:analyzed_session_ids checkpoint))
        meta-dir     (io/file session-meta-dir)
        facets-dir   (io/file facets-dir)
        updated      (reduce
                      (fn [cp session-id]
                        (if (contains? analyzed-ids session-id)
                          cp
                          (let [meta-file (io/file meta-dir (str session-id ".json"))]
                            (if-not (.exists meta-file)
                              cp
                              (let [session-meta (json/parse-string (slurp meta-file) true)
                                    week-key     (keyword (iso-week (:start_time session-meta
                                                                               "1970-01-01T00:00:00Z")))
                                    facet-file   (io/file facets-dir (str session-id ".json"))
                                    checkpoint'  (if (.exists facet-file)
                                                   (let [facet (json/parse-string (slurp facet-file) true)]
                                                     (update-in cp [:weekly_buckets week-key]
                                                                #(merge-facet-into-bucket (or % {}) facet)))
                                                   (update-in cp [:weekly_buckets week-key :session_count]
                                                              (fnil inc 0)))]
                                (update checkpoint' :analyzed_session_ids conj session-id))))))
                      (-> checkpoint
                          (assoc :config_snapshot config-snapshot)
                          (assoc :last_updated (str (Instant/now))))
                      new-session-ids)]
    (.mkdirs (.getParentFile (io/file checkpoint-path)))
    (spit checkpoint-path (json/generate-string updated {:pretty true}))
    updated))

(defn- parse-args [args]
  (loop [args args acc {:new-session-ids []}]
    (cond
      (empty? args) acc
      (= "--new-session-ids" (first args))
      (let [ids (vec (take-while #(not (.startsWith % "--")) (rest args)))]
        (recur (drop (inc (count ids)) (rest args))
               (assoc acc :new-session-ids ids)))
      (< (count args) 2) acc
      :else (recur (drop 2 args) (assoc acc (first args) (second args))))))

(defn -main [& args]
  (let [opts     (parse-args args)
        snapshot (json/parse-string (get opts "--config-snapshot" "{}") true)
        updated  (merge-into-checkpoint
                  (get opts "--checkpoint")
                  (get opts "--session-meta-dir")
                  (get opts "--facets-dir")
                  (:new-session-ids opts)
                  snapshot)]
    (println (str "Checkpoint updated: "
                  (count (:analyzed_session_ids updated))
                  " total sessions analyzed"))))
