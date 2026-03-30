(ns usage-insights.collect-sessions-test
  (:require [clojure.test :refer [deftest is testing]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [usage-insights.collect-sessions :as sut])
  (:import [java.time Instant]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- tmp-dir []
  (str (Files/createTempDirectory "ui-test" (into-array FileAttribute []))))

(defn- write-json [path data]
  (io/make-parents path)
  (spit path (json/generate-string data)))

(def ^:private session-fixture
  {:project_path "/test" :duration_minutes 10 :user_message_count 5
   :assistant_message_count 5 :tool_counts {} :languages {}
   :git_commits 0 :git_pushes 0 :input_tokens 100
   :output_tokens 200 :first_prompt "test"
   :user_interruptions 0 :user_response_times []
   :tool_errors 0 :tool_error_categories {}
   :uses_task_agent false :uses_mcp false
   :uses_web_search false :uses_web_fetch false
   :lines_added 0 :lines_removed 0 :files_modified 0
   :message_hours []})

(defn- make-session [dir id start-time]
  (write-json (str dir "/session-meta/" id ".json")
              (assoc session-fixture
                     :session_id id
                     :start_time start-time
                     :user_message_timestamps [start-time])))

(defn- make-facet [dir id]
  (write-json (str dir "/facets/" id ".json") {:session_id id}))

(deftest load-checkpoint-returns-empty-when-missing
  (let [result (sut/load-checkpoint "/nonexistent/checkpoint.json")]
    (is (= [] (:analyzed_session_ids result)))
    (is (= {} (:weekly_buckets result)))
    (is (= 1 (:version result)))))

(deftest load-checkpoint-reads-existing-file
  (let [dir (tmp-dir)
        path (str dir "/checkpoint.json")
        data {:version 1 :analyzed_session_ids ["abc"] :weekly_buckets {}}]
    (spit path (json/generate-string data))
    (is (= ["abc"] (:analyzed_session_ids (sut/load-checkpoint path))))))

(deftest parse-since-days
  (let [result (sut/parse-since "30d")
        expected (.minus (Instant/now) (java.time.Duration/ofDays 30))
        diff (Math/abs (- (.toEpochMilli result) (.toEpochMilli expected)))]
    (is (< diff 5000))))

(deftest parse-since-weeks
  (let [result (sut/parse-since "4w")
        expected (.minus (Instant/now) (java.time.Duration/ofDays 28))
        diff (Math/abs (- (.toEpochMilli result) (.toEpochMilli expected)))]
    (is (< diff 5000))))

(deftest parse-since-iso-date
  (let [result (sut/parse-since "2026-02-01")]
    (is (.startsWith (str result) "2026-02-01"))))

(deftest parse-since-nil-returns-nil
  (is (nil? (sut/parse-since nil))))

(deftest load-sessions-reads-all-json
  (let [dir (tmp-dir)]
    (make-session dir "id-1" "2026-03-01T10:00:00Z")
    (make-session dir "id-2" "2026-03-02T10:00:00Z")
    (let [sessions (sut/load-sessions (str dir "/session-meta"))]
      (is (= 2 (count sessions)))
      (is (= #{"id-1" "id-2"} (set (map :session_id sessions)))))))

(deftest collect-excludes-already-analyzed
  (let [dir (tmp-dir)]
    (make-session dir "old-id" "2026-03-01T10:00:00Z")
    (make-session dir "new-id" "2026-03-02T10:00:00Z")
    (let [checkpoint {:version 1 :analyzed_session_ids ["old-id"] :weekly_buckets {}}
          result (sut/collect (str dir "/session-meta")
                              (str dir "/facets")
                              checkpoint nil)]
      (is (= 1 (count (:new_sessions result))))
      (is (= "new-id" (:session_id (first (:new_sessions result))))))))

(deftest collect-marks-unfaceted-sessions
  (let [dir (tmp-dir)]
    (make-session dir "with-facet" "2026-03-01T10:00:00Z")
    (make-session dir "no-facet"   "2026-03-02T10:00:00Z")
    (make-facet dir "with-facet")
    (let [checkpoint {:version 1 :analyzed_session_ids [] :weekly_buckets {}}
          result (sut/collect (str dir "/session-meta")
                              (str dir "/facets")
                              checkpoint nil)]
      (is (some #{"no-facet"} (:unfaceted_ids result)))
      (is (not (some #{"with-facet"} (:unfaceted_ids result)))))))

(deftest collect-window-filters-by-since
  (let [dir (tmp-dir)]
    (make-session dir "old"    "2026-01-01T10:00:00Z")
    (make-session dir "recent" "2026-03-01T10:00:00Z")
    (let [checkpoint {:version 1 :analyzed_session_ids [] :weekly_buckets {}}
          since     (Instant/parse "2026-02-01T00:00:00Z")
          result    (sut/collect (str dir "/session-meta")
                                 (str dir "/facets")
                                 checkpoint since)
          window-ids (set (map :session_id (:window_sessions result)))]
      (is (contains? window-ids "recent"))
      (is (not (contains? window-ids "old"))))))

(deftest collect-window-includes-all-when-since-nil
  (let [dir (tmp-dir)]
    (make-session dir "old"    "2026-01-01T10:00:00Z")
    (make-session dir "recent" "2026-03-01T10:00:00Z")
    (let [checkpoint {:version 1 :analyzed_session_ids [] :weekly_buckets {}}
          result    (sut/collect (str dir "/session-meta")
                                 (str dir "/facets")
                                 checkpoint nil)]
      (is (= 2 (count (:window_sessions result)))))))
