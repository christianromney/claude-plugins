(ns usage-insights.collect-sessions
  (:require [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.time Instant Duration]))

(defn load-checkpoint
  "Read checkpoint.json from path; return empty default struct if file does not exist."
  [path]
  (let [f (io/file path)]
    (if (.exists f)
      (json/parse-string (slurp f) true)
      {:version 1 :analyzed_session_ids [] :weekly_buckets {}
       :config_snapshot {} :last_updated ""})))

(defn parse-since
  "Return an Instant from a duration string (30d, 4w) or ISO date, or nil."
  [since-str]
  (when since-str
    (let [now (Instant/now)]
      (cond
        (re-matches #"\d+d" since-str)
        (.minus now (Duration/ofDays (Long/parseLong (subs since-str 0 (dec (count since-str))))))

        (re-matches #"\d+w" since-str)
        (.minus now (Duration/ofDays (* 7 (Long/parseLong (subs since-str 0 (dec (count since-str)))))))

        :else
        (Instant/parse (if (.contains since-str "T")
                         since-str
                         (str since-str "T00:00:00Z")))))))

(defn load-sessions
  "Read all session-meta JSON files from session-dir; skip files that fail to parse."
  [session-dir]
  (let [dir (io/file session-dir)]
    (if (.isDirectory dir)
      (->> (.listFiles dir)
           (filter #(.. % getName (endsWith ".json")))
           (keep (fn [f]
                   (try (json/parse-string (slurp f) true)
                        (catch Exception e
                          (binding [*out* *err*]
                            (println (str "WARNING: failed to parse session file " (.getName f) ": " (.getMessage e))))
                          nil)))))
      [])))

(defn collect
  "Return {:new_sessions [...] :unfaceted_ids [...] :window_sessions [...]}.
   new_sessions: not yet in checkpoint.
   unfaceted_ids: new sessions without an existing facet file.
   window_sessions: all sessions on or after since (or all if since is nil)."
  [session-dir facets-dir checkpoint since]
  (let [analyzed-ids (set (:analyzed_session_ids checkpoint))
        all-sessions (load-sessions session-dir)
        new-sessions (remove #(analyzed-ids (:session_id %)) all-sessions)
        unfaceted-ids (->> new-sessions
                           (remove #(.exists (io/file facets-dir
                                                      (str (:session_id %) ".json"))))
                           (mapv :session_id))
        window-sessions (if since
                          (filterv #(.isAfter (Instant/parse (:start_time %)) since)
                                   all-sessions)
                          (vec all-sessions))]
    {:new_sessions    (mapv :session_id new-sessions)
     :unfaceted_ids   unfaceted-ids
     :window_sessions (mapv :session_id window-sessions)}))

(defn- parse-args
  "Parse a flat sequence of alternating flag/value pairs into a map."
  [args]
  (loop [args args acc {}]
    (if (< (count args) 2)
      acc
      (recur (drop 2 args) (assoc acc (first args) (second args))))))

(defn -main
  "CLI entry point. Accepts --checkpoint, --session-dir, --facets-dir, --since flags."
  [& args]
  (let [options    (parse-args args)
        checkpoint (load-checkpoint (get options "--checkpoint" ""))
        since      (parse-since (get options "--since"))
        result     (collect (get options "--session-dir")
                            (get options "--facets-dir")
                            checkpoint since)]
    (println (json/generate-string result))))
