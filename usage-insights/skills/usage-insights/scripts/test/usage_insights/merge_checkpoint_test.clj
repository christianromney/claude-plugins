(ns usage-insights.merge-checkpoint-test
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [usage-insights.merge-checkpoint :as sut])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- tmp-dir []
  (str (Files/createTempDirectory "ui-merge-test" (into-array FileAttribute []))))

(defn- write-json [path data]
  (io/make-parents path)
  (spit path (json/generate-string data)))

(defn- make-session-meta [dir id start-time]
  (write-json (str dir "/session-meta/" id ".json")
              {:session_id id :start_time start-time}))

(defn- make-facet [dir id & {:keys [friction outcome satisfaction goals]
                              :or   {friction {} outcome "mostly_achieved"
                                     satisfaction "likely_satisfied" goals {}}}]
  (write-json (str dir "/facets/" id ".json")
              {:session_id id :friction_counts friction :outcome outcome
               :user_satisfaction_counts {satisfaction 1} :goal_categories goals}))

(deftest iso-week-returns-correct-format
  ;; 2026-03-02 is ISO week 10 of 2026
  (is (= "2026-W10" (sut/iso-week "2026-03-02T10:00:00Z"))))

(deftest iso-week-handles-year-boundary
  ;; 2025-12-31 is ISO week 1 of 2026
  (is (= "2026-W01" (sut/iso-week "2025-12-31T12:00:00Z"))))

(deftest add-counts-merges-dicts
  (is (= {:a 1 :b 5 :c 1}
         (sut/add-counts {:a 1 :b 2} {:b 3 :c 1}))))

(deftest add-counts-into-empty
  (is (= {:x 2} (sut/add-counts {} {:x 2}))))

(deftest merge-facet-into-bucket-counts-session
  (let [bucket (sut/merge-facet-into-bucket
                {} {:friction_counts {} :outcome "mostly_achieved"
                    :user_satisfaction_counts {:likely_satisfied 1}
                    :goal_categories {}})]
    (is (= 1 (:session_count bucket)))))

(deftest merge-facet-into-bucket-accumulates-friction
  (let [initial {:session_count 1 :friction_counts {:wrong_approach 2}}
        result  (sut/merge-facet-into-bucket
                 initial {:friction_counts {:wrong_approach 1 :buggy_code 1}
                           :outcome "partial"
                           :user_satisfaction_counts {}
                           :goal_categories {}})]
    (is (= 3 (get-in result [:friction_counts :wrong_approach])))
    (is (= 1 (get-in result [:friction_counts :buggy_code])))))

(deftest merge-creates-checkpoint-when-missing
  (let [dir  (tmp-dir)
        cp   (str dir "/checkpoint.json")]
    (make-session-meta dir "s1" "2026-03-02T10:00:00Z")
    (make-facet dir "s1")
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                ["s1"] [] {})
    (let [result (json/parse-string (slurp cp) true)]
      (is (some #{"s1"} (:analyzed_session_ids result)))
      (is (contains? (:weekly_buckets result) :2026-W10)))))

(deftest merge-skips-already-analyzed
  (let [dir (tmp-dir)
        cp  (str dir "/checkpoint.json")]
    (make-session-meta dir "s1" "2026-03-02T10:00:00Z")
    (make-facet dir "s1" :friction {:wrong_approach 1})
    (write-json cp {:version 1 :analyzed_session_ids ["s1"]
                    :weekly_buckets {} :config_snapshot {} :last_updated ""})
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                ["s1"] [] {})
    (let [result (json/parse-string (slurp cp) true)]
      ;; weekly_buckets should remain empty — s1 was already analyzed
      (is (= {} (:weekly_buckets result))))))

(deftest merge-handles-session-without-facet
  (let [dir (tmp-dir)
        cp  (str dir "/checkpoint.json")]
    (make-session-meta dir "no-facet" "2026-03-02T10:00:00Z")
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                ["no-facet"] [] {})
    (let [result (json/parse-string (slurp cp) true)]
      (is (some #{"no-facet"} (:analyzed_session_ids result)))
      (is (= 1 (get-in result [:weekly_buckets :2026-W10 :session_count]))))))

(deftest parse-args-preserves-config-snapshot-after-many-ids
  ;; Regression: previous multi-arg --new-session-ids design dropped
  ;; --config-snapshot when many IDs were present.
  (let [dir      (tmp-dir)
        cp       (str dir "/checkpoint.json")
        many-ids (clojure.string/join "," (map #(str "id-" %) (range 60)))
        snapshot (json/generate-string {:hooks ["guard.sh"] :skills ["foo"]})]
    (doseq [i (range 60)]
      (make-session-meta dir (str "id-" i) "2026-03-02T10:00:00Z"))
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                (mapv #(str "id-" %) (range 60))
                                []
                                (json/parse-string snapshot true))
    (let [result (json/parse-string (slurp cp) true)]
      (is (= ["guard.sh"] (get-in result [:config_snapshot :hooks])))
      (is (= 60 (count (:analyzed_session_ids result)))))))

(deftest merge-updates-config-snapshot
  (let [dir      (tmp-dir)
        cp       (str dir "/checkpoint.json")
        snapshot {:hooks ["my-hook.sh"] :skills ["confluence"]}]
    (make-session-meta dir "s1" "2026-03-02T10:00:00Z")
    (make-facet dir "s1")
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                ["s1"] [] snapshot)
    (let [result (json/parse-string (slurp cp) true)]
      (is (= ["my-hook.sh"] (get-in result [:config_snapshot :hooks]))))))

(deftest merge-stores-ignored-session-ids
  (let [dir (tmp-dir)
        cp  (str dir "/checkpoint.json")]
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                [] ["bad-id-1" "bad-id-2"] {})
    (let [result (json/parse-string (slurp cp) true)]
      (is (= #{"bad-id-1" "bad-id-2"}
             (set (:ignored_session_ids result)))))))

(deftest merge-appends-to-existing-ignored-session-ids
  (let [dir (tmp-dir)
        cp  (str dir "/checkpoint.json")]
    (write-json cp {:version 1 :analyzed_session_ids []
                    :ignored_session_ids ["existing-bad"]
                    :weekly_buckets {} :config_snapshot {} :last_updated ""})
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                [] ["new-bad"] {})
    (let [result (json/parse-string (slurp cp) true)]
      (is (= #{"existing-bad" "new-bad"}
             (set (:ignored_session_ids result)))))))

(deftest merge-deduplicates-ignored-session-ids
  (let [dir (tmp-dir)
        cp  (str dir "/checkpoint.json")]
    (write-json cp {:version 1 :analyzed_session_ids []
                    :ignored_session_ids ["dup-id"]
                    :weekly_buckets {} :config_snapshot {} :last_updated ""})
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                [] ["dup-id"] {})
    (let [result (json/parse-string (slurp cp) true)]
      (is (= 1 (count (:ignored_session_ids result)))))))
