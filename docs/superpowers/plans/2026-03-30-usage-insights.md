# usage-insights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Claude Code skill that produces time-aware, config-aware HTML usage insight reports from Claude Code session data, with incremental checkpoint-driven synthesis.

**Architecture:** Three Clojure namespaces handle data I/O (session collection, checkpoint merging, config reading); the SKILL.md orchestrates six phases in sequence, with Claude handling narrative synthesis. All output goes to `~/.claude/usage-insights/`.

**Tech Stack:** Clojure 1.12, tools.deps (`deps.edn`), cheshire (JSON), clojure.test + cognitect test-runner, HTML/CSS (inline, matching existing report.html structure).

**Spec:** [`docs/superpowers/specs/2026-03-30-usage-insights-skill-design.md`](../specs/2026-03-30-usage-insights-skill-design.md)

---

## File Structure

```
resources/christian.romney/skills/usage-insights/
├── SKILL.md                                    # skill orchestration + prompts (via /skill-creator)
├── references/
│   ├── friction-taxonomy.md                    # canonical friction category definitions
│   ├── facet-prompt.md                         # prompt template for generating facets
│   └── report-structure.md                     # HTML section inventory for report generation
└── scripts/
    ├── deps.edn                                # tools.deps config
    ├── src/
    │   └── usage_insights/
    │       ├── collect_sessions.clj            # phase 1: session collection and filtering
    │       ├── merge_checkpoint.clj            # phase 3: merge facets into checkpoint
    │       └── read_config.clj                 # phase 4: config inventory reader
    └── test/
        └── usage_insights/
            ├── collect_sessions_test.clj
            ├── merge_checkpoint_test.clj
            └── read_config_test.clj
```

Scripts are invoked from within `scripts/` using `clojure -M:<alias>`. The SKILL.md locates the scripts dir at runtime using `git rev-parse --show-toplevel`.

---

## Task 1: Create skill directory, deps.edn, and reference files

**Files:**
- Create: `resources/christian.romney/skills/usage-insights/scripts/deps.edn`
- Create: `resources/christian.romney/skills/usage-insights/references/friction-taxonomy.md`
- Create: `resources/christian.romney/skills/usage-insights/references/facet-prompt.md`
- Create: `resources/christian.romney/skills/usage-insights/references/report-structure.md`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p resources/christian.romney/skills/usage-insights/references
mkdir -p resources/christian.romney/skills/usage-insights/scripts/src/usage_insights
mkdir -p resources/christian.romney/skills/usage-insights/scripts/test/usage_insights
```

- [ ] **Step 2: Write deps.edn**

Create `resources/christian.romney/skills/usage-insights/scripts/deps.edn`:

```clojure
{:paths ["src"]
 :deps {org.clojure/clojure {:mvn/version "1.12.0"}
        cheshire/cheshire    {:mvn/version "5.13.0"}}
 :aliases
 {:test    {:extra-paths ["test"]
            :extra-deps  {io.github.cognitect-labs/test-runner
                          {:git/tag "v0.5.1" :git/sha "dfb30dd"}}
            :main-opts   ["-m" "cognitect.test-runner"
                          "--namespace-regex" "usage-insights.*-test"]}
  :collect {:main-opts ["-m" "usage-insights.collect-sessions"]}
  :merge   {:main-opts ["-m" "usage-insights.merge-checkpoint"]}
  :config  {:main-opts ["-m" "usage-insights.read-config"]}}}
```

- [ ] **Step 3: Write friction-taxonomy.md**

Create `resources/christian.romney/skills/usage-insights/references/friction-taxonomy.md`:

```markdown
# Friction Taxonomy

Use exactly these category keys when generating facets. The taxonomy is open
for extension: add a new snake_case key only when observed behavior does not
fit any existing category.

| Key | Description |
|---|---|
| `wrong_approach` | Claude chose a method, tool, or strategy the user did not want |
| `user_rejected_action` | Claude took an action the user interrupted or rolled back |
| `excessive_changes` | Claude modified more than was asked |
| `misunderstood_request` | Claude misread the user's intent |
| `buggy_code` | Claude produced code with defects |
| `missed_requirement` | Claude omitted part of the requested work |
| `external_tool_failure` | An MCP server, plugin, or external tool failed |
```

- [ ] **Step 4: Write facet-prompt.md**

Create `resources/christian.romney/skills/usage-insights/references/facet-prompt.md`:

```markdown
# Facet Generation Prompt

Use this prompt when generating a facet for a session that lacks one.
Replace `{SESSION_META_JSON}` with the raw JSON of the session-meta file.
Respond with valid JSON only — no prose, no markdown fences.

---

Analyze this Claude Code session and produce a JSON facet.

Session data:
{SESSION_META_JSON}

Output this exact schema:
{
  "session_id": "<copy from input>",
  "underlying_goal": "<one sentence: what was the user trying to accomplish?>",
  "goal_categories": {"<snake_case_category>": 1},
  "outcome": "<one of: mostly_achieved | partial | not_achieved>",
  "user_satisfaction_counts": {"<one of: likely_satisfied | mixed | likely_frustrated>": 1},
  "claude_helpfulness": "<one of: essential | helpful | limited | unhelpful>",
  "session_type": "<one of: multi_task | iterative_refinement | exploration | quick_question>",
  "friction_counts": {"<key from taxonomy below>": <integer>},
  "friction_detail": "<one sentence describing friction, or empty string>",
  "primary_success": "<one phrase describing the main thing that went well, or empty string>",
  "brief_summary": "<two sentences max summarizing the session>"
}

Friction taxonomy — use only these keys, or add a new snake_case key if none fit:
- wrong_approach: Claude chose a method, tool, or strategy the user did not want
- user_rejected_action: Claude took an action the user interrupted or rolled back
- excessive_changes: Claude modified more than was asked
- misunderstood_request: Claude misread the user's intent
- buggy_code: Claude produced code with defects
- missed_requirement: Claude omitted part of the requested work
- external_tool_failure: An MCP server, plugin, or external tool failed

If there was no friction, set friction_counts to {} and friction_detail to "".
```

- [ ] **Step 5: Write report-structure.md**

Create `resources/christian.romney/skills/usage-insights/references/report-structure.md`:

```markdown
# Report Structure Reference

The HTML report matches the structure of `~/.claude/usage-data/report.html`.
Use the same CSS class names and section IDs.

## Sections

| Section ID | Nav label | Content |
|---|---|---|
| `section-work` | What You Work On | `.project-area` cards |
| `section-usage` | How You Use CC | `.narrative` + charts |
| `section-wins` | Impressive Things | `.big-win` cards |
| `section-friction` | Where Things Go Wrong | `.friction-category` cards |
| `section-features` | Features to Try | `.feature-card` cards |
| `section-patterns` | New Usage Patterns | `.pattern-card` cards |
| `section-horizon` | On the Horizon | `.horizon-card` cards |
| `section-feedback` | Team Feedback | `.feedback-card` cards |

## New CSS Classes

```css
.trend-badge { display:inline-block; font-size:11px; font-weight:600;
  padding:2px 8px; border-radius:4px; margin-left:8px; }
.trend-declining  { background:#dcfce7; color:#166534; }
.trend-persistent { background:#fef9c3; color:#854d0e; }
.trend-emerging   { background:#fee2e2; color:#991b1b; }
.trend-note { font-size:12px; color:#64748b; margin-top:4px; }

.config-pill { display:inline-block; font-size:11px; font-weight:600;
  padding:2px 8px; border-radius:4px; margin-left:8px; }
.config-actionable { background:#dbeafe; color:#1e40af; }
.config-in-place   { background:#f1f5f9; color:#64748b; }
.config-partial    { background:#fef3c7; color:#92400e; }
.feature-card.muted { opacity:0.55; }

.report-meta { font-size:12px; color:#64748b; background:#f8fafc;
  border:1px solid #e2e8f0; border-radius:6px; padding:8px 14px;
  margin-bottom:24px; display:flex; gap:16px; flex-wrap:wrap; }
.report-meta span::before { content:"• "; }
.report-meta span:first-child::before { content:""; }
```

## Metadata Bar

Render immediately after `.subtitle`:
```html
<div class="report-meta">
  <span>{date_range}</span>
  <span>{session_count} sessions in scope</span>
  <span>--since {value}</span>   <!-- omit if no --since used -->
  <span>checkpoint: {path}</span>
</div>
```

## Trend Badge (inside .friction-category)

Add after `.friction-title`:
```html
<span class="trend-badge trend-{declining|persistent|emerging}">
  {↓ Declining | → Persistent | ↑ Emerging}
</span>
<div class="trend-note">{e.g. "5 in January, 0 since February"}</div>
```
Omit both elements when fewer than 3 weeks of data.

## Config Pill (inside .feature-card)

Add after `.feature-title`:
```html
<span class="config-pill config-{actionable|in-place|partial}">
  {Actionable | Already in place | Partially addressed}
</span>
```
Add class `muted` to `.feature-card` when status is `in-place`.
```

- [ ] **Step 6: Commit**

```bash
git add resources/christian.romney/skills/usage-insights/
git commit -m "Add usage-insights scaffold: deps.edn and reference files"
```

---

## Task 2: Write and test collect_sessions.clj

**Files:**
- Create: `resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/collect_sessions.clj`
- Create: `resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/collect_sessions_test.clj`

- [ ] **Step 1: Write the failing tests**

Create `resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/collect_sessions_test.clj`:

```clojure
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

(defn- make-session [dir id start-time]
  (write-json (str dir "/session-meta/" id ".json")
              {:session_id id :start_time start-time :project_path "/test"
               :duration_minutes 10 :user_message_count 5
               :assistant_message_count 5 :tool_counts {} :languages {}
               :git_commits 0 :git_pushes 0 :input_tokens 100
               :output_tokens 200 :first_prompt "test"
               :user_interruptions 0 :user_response_times []
               :tool_errors 0 :tool_error_categories {}
               :uses_task_agent false :uses_mcp false
               :uses_web_search false :uses_web_fetch false
               :lines_added 0 :lines_removed 0 :files_modified 0
               :message_hours [] :user_message_timestamps [start-time]}))

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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test 2>&1 | head -20
```

Expected: compilation error — `usage-insights.collect-sessions` not found.

- [ ] **Step 3: Write collect_sessions.clj**

Create `resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/collect_sessions.clj`:

```clojure
(ns usage-insights.collect-sessions
  (:require [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.time Instant Duration]))

(defn load-checkpoint [path]
  (let [f (io/file path)]
    (if (.exists f)
      (json/parse-string (slurp f) true)
      {:version 1 :analyzed_session_ids [] :weekly_buckets {}
       :config_snapshot {} :last_updated ""})))

(defn parse-since
  "Return an Instant from a duration string (30d, 4w) or ISO date, or nil."
  [since-str]
  (when since-str
    (cond
      (re-matches #"\d+d" since-str)
      (.minus (Instant/now)
              (Duration/ofDays (Long/parseLong (subs since-str 0 (dec (count since-str))))))

      (re-matches #"\d+w" since-str)
      (.minus (Instant/now)
              (Duration/ofDays (* 7 (Long/parseLong (subs since-str 0 (dec (count since-str)))))))

      :else
      (Instant/parse (if (.contains since-str "T")
                       since-str
                       (str since-str "T00:00:00Z"))))))

(defn load-sessions [session-dir]
  (->> (.listFiles (io/file session-dir))
       (filter #(.. % getName (endsWith ".json")))
       (keep (fn [f]
               (try (json/parse-string (slurp f) true)
                    (catch Exception _ nil))))))

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
    {:new_sessions  (vec new-sessions)
     :unfaceted_ids unfaceted-ids
     :window_sessions window-sessions}))

(defn- parse-args [args]
  (loop [args args acc {}]
    (if (< (count args) 2)
      acc
      (recur (drop 2 args) (assoc acc (first args) (second args))))))

(defn -main [& args]
  (let [opts  (parse-args args)
        cp    (load-checkpoint (get opts "--checkpoint" ""))
        since (parse-since (get opts "--since"))
        result (collect (get opts "--session-dir")
                        (get opts "--facets-dir")
                        cp since)]
    (println (json/generate-string result))))
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/collect_sessions.clj \
        resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/collect_sessions_test.clj
git commit -m "Add collect-sessions namespace with tests"
```

---

## Task 3: Write and test merge_checkpoint.clj

**Files:**
- Create: `resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/merge_checkpoint.clj`
- Create: `resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/merge_checkpoint_test.clj`

- [ ] **Step 1: Write the failing tests**

Create `resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/merge_checkpoint_test.clj`:

```clojure
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
                                ["s1"] {})
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
                                ["s1"] {})
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
                                ["no-facet"] {})
    (let [result (json/parse-string (slurp cp) true)]
      (is (some #{"no-facet"} (:analyzed_session_ids result)))
      (is (= 1 (get-in result [:weekly_buckets :2026-W10 :session_count]))))))

(deftest merge-updates-config-snapshot
  (let [dir      (tmp-dir)
        cp       (str dir "/checkpoint.json")
        snapshot {:hooks ["my-hook.sh"] :skills ["confluence"]}]
    (make-session-meta dir "s1" "2026-03-02T10:00:00Z")
    (make-facet dir "s1")
    (sut/merge-into-checkpoint cp
                                (str dir "/session-meta")
                                (str dir "/facets")
                                ["s1"] snapshot)
    (let [result (json/parse-string (slurp cp) true)]
      (is (= ["my-hook.sh"] (get-in result [:config_snapshot :hooks]))))))
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test 2>&1 | head -20
```

Expected: compilation error — `usage-insights.merge-checkpoint` not found.

- [ ] **Step 3: Write merge_checkpoint.clj**

Create `resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/merge_checkpoint.clj`:

```clojure
(ns usage-insights.merge-checkpoint
  (:require [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.time Instant]
           [java.time.temporal IsoFields]))

(defn iso-week [start-time]
  (let [instant (Instant/parse start-time)
        zdt     (.atZone instant java.time.ZoneOffset/UTC)
        week    (.get zdt IsoFields/WEEK_OF_WEEK_BASED_YEAR)
        year    (.get zdt IsoFields/WEEK_BASED_YEAR)]
    (format "%d-W%02d" year week)))

(defn add-counts [target source]
  (reduce (fn [acc [k v]] (update acc k (fnil + 0) v)) target source))

(defn merge-facet-into-bucket [bucket facet]
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
                                    cp'          (if (.exists facet-file)
                                                   (let [facet (json/parse-string (slurp facet-file) true)]
                                                     (update-in cp [:weekly_buckets week-key]
                                                                #(merge-facet-into-bucket (or % {}) facet)))
                                                   (update-in cp [:weekly_buckets week-key :session_count]
                                                              (fnil inc 0)))]
                                (update cp' :analyzed_session_ids conj session-id))))))
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test
```

Expected: all tests PASS.

- [ ] **Step 5: Commit**

```bash
git add resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/merge_checkpoint.clj \
        resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/merge_checkpoint_test.clj
git commit -m "Add merge-checkpoint namespace with tests"
```

---

## Task 4: Write and test read_config.clj

**Files:**
- Create: `resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/read_config.clj`
- Create: `resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/read_config_test.clj`

- [ ] **Step 1: Write the failing tests**

Create `resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/read_config_test.clj`:

```clojure
(ns usage-insights.read-config-test
  (:require [clojure.test :refer [deftest is]]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [usage-insights.read-config :as sut])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- tmp-dir []
  (str (Files/createTempDirectory "ui-config-test" (into-array FileAttribute []))))

(defn- touch [path]
  (io/make-parents path)
  (spit path ""))

(deftest find-claude-md-paths-finds-global-md
  (let [dir (tmp-dir)]
    (spit (str dir "/CLAUDE.md") "# Config")
    (is (some #{(str dir "/CLAUDE.md")} (sut/find-claude-md-paths dir)))))

(deftest find-claude-md-paths-missing-returns-empty
  (is (= [] (sut/find-claude-md-paths (tmp-dir)))))

(deftest find-hooks-finds-sh-files
  (let [dir (tmp-dir)]
    (touch (str dir "/hooks/credential-guard.sh"))
    (touch (str dir "/hooks/pre-commit.sh"))
    (let [result (sut/find-hooks dir)]
      (is (some #{"credential-guard.sh"} result))
      (is (some #{"pre-commit.sh"} result)))))

(deftest find-hooks-reads-settings-json
  (let [dir (tmp-dir)]
    (touch (str dir "/hooks/.keep"))
    (spit (str dir "/settings.json")
          (json/generate-string
           {:hooks [{:matcher "Bash"
                     :hooks [{:type "command" :command "run" :name "my-hook"}]}]}))
    (is (some #{"my-hook"} (sut/find-hooks dir)))))

(deftest find-hooks-missing-dirs-returns-empty
  (is (= [] (sut/find-hooks (tmp-dir)))))

(deftest find-skills-finds-skill-md-names
  (let [dir (tmp-dir)]
    (touch (str dir "/plugins/cache/my-plugin/1.0.0/skills/my-skill/SKILL.md"))
    (is (some #{"my-skill"} (sut/find-skills dir)))))

(deftest find-skills-deduplicates-across-versions
  (let [dir (tmp-dir)]
    (touch (str dir "/plugins/cache/my-plugin/1.0.0/skills/my-skill/SKILL.md"))
    (touch (str dir "/plugins/cache/my-plugin/1.0.1/skills/my-skill/SKILL.md"))
    (let [result (sut/find-skills dir)]
      (is (= 1 (count (filter #{"my-skill"} result)))))))

(deftest find-skills-missing-plugins-returns-empty
  (is (= [] (sut/find-skills (tmp-dir)))))

(deftest read-config-returns-combined-inventory
  (let [dir (tmp-dir)]
    (spit (str dir "/CLAUDE.md") "# Config")
    (touch (str dir "/hooks/my-hook.sh"))
    (touch (str dir "/plugins/cache/p/1.0/skills/my-skill/SKILL.md"))
    (let [result (sut/read-config dir)]
      (is (some #{(str dir "/CLAUDE.md")} (:claude_md_paths result)))
      (is (some #{"my-hook.sh"} (:hooks result)))
      (is (some #{"my-skill"} (:skills result))))))
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test 2>&1 | head -20
```

Expected: compilation error — `usage-insights.read-config` not found.

- [ ] **Step 3: Write read_config.clj**

Create `resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/read_config.clj`:

```clojure
(ns usage-insights.read-config
  (:require [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn find-claude-md-paths [claude-dir]
  (let [f (io/file claude-dir "CLAUDE.md")]
    (if (.exists f) [(str f)] [])))

(defn find-hooks [claude-dir]
  (let [hooks-dir     (io/file claude-dir "hooks")
        settings-file (io/file claude-dir "settings.json")
        from-dir      (if (.exists hooks-dir)
                        (->> (.listFiles hooks-dir)
                             (filter #(.. % getName (endsWith ".sh")))
                             (map #(.getName %)))
                        [])
        from-settings (if (.exists settings-file)
                        (try
                          (->> (json/parse-string (slurp settings-file) true)
                               :hooks
                               (mapcat :hooks)
                               (keep :name))
                          (catch Exception _ []))
                        [])]
    (vec (sort (distinct (concat from-dir from-settings))))))

(defn find-skills [claude-dir]
  (let [cache-dir (io/file claude-dir "plugins" "cache")]
    (if-not (.exists cache-dir)
      []
      (->> (file-seq cache-dir)
           (filter #(= "SKILL.md" (.getName %)))
           (map #(.. % getParentFile getName))
           distinct
           sort
           vec))))

(defn read-config
  "Return config inventory map. Pure function for testability."
  [claude-dir]
  {:claude_md_paths (find-claude-md-paths claude-dir)
   :hooks           (find-hooks claude-dir)
   :skills          (find-skills claude-dir)})

(defn -main [& args]
  (let [opts      (apply hash-map args)
        claude-dir (io/file (get opts "--claude-dir" (str (System/getProperty "user.home") "/.claude")))
        inventory  (read-config claude-dir)]
    (println (json/generate-string inventory {:pretty true}))))
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test
```

Expected: all tests PASS.

- [ ] **Step 5: Run the full test suite**

```bash
cd resources/christian.romney/skills/usage-insights/scripts
clojure -M:test
```

Expected: all tests PASS across all three namespaces.

- [ ] **Step 6: Commit**

```bash
git add resources/christian.romney/skills/usage-insights/scripts/src/usage_insights/read_config.clj \
        resources/christian.romney/skills/usage-insights/scripts/test/usage_insights/read_config_test.clj
git commit -m "Add read-config namespace with tests"
```

---

## Task 5: Scaffold SKILL.md with /skill-creator

**Files:**
- Create: `resources/christian.romney/skills/usage-insights/SKILL.md`

- [ ] **Step 1: Invoke /skill-creator with the following context**

```
Skill name: usage-insights
Location: resources/christian.romney/skills/usage-insights/SKILL.md
Description: Generate time-aware, config-aware HTML usage insight reports
  from Claude Code session data. Improves on the built-in /insights command
  by adding trend classification (Declining/Persistent/Emerging) per friction
  category, config-awareness (suppress suggestions already addressed in
  CLAUDE.md, hooks, and skills), and checkpoint-driven incremental synthesis
  so report cost scales only with new sessions.
User invocable: true
Arguments:
  - name: since
    description: Restrict report to sessions on or after this date or duration.
      Examples: 30d, 4w, 2026-02-01. Omit for full history.
    required: false
Allowed tools: Bash, Read, Write, Glob
```

- [ ] **Step 2: Verify the scaffolded SKILL.md has correct frontmatter**

Confirm it contains:
```yaml
---
name: usage-insights
description: ...
user_invocable: true
arguments:
  - name: since
    ...
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
---
```

- [ ] **Step 3: Commit scaffold**

```bash
git add resources/christian.romney/skills/usage-insights/SKILL.md
git commit -m "Scaffold usage-insights SKILL.md via skill-creator"
```

---

## Task 6: Write SKILL.md orchestration body

**Files:**
- Modify: `resources/christian.romney/skills/usage-insights/SKILL.md`

- [ ] **Step 1: Write the preamble and scripts-dir resolver**

Append to SKILL.md body after the frontmatter:

```markdown
# usage-insights

Generate a time-aware, config-aware HTML usage insight report from Claude Code
session data. Output: `~/.claude/usage-insights/report-{timestamp}.html`.

## Preamble: Locate Scripts Directory

Before executing any phase, determine the scripts directory:

```bash
REPO_ROOT=$(git rev-parse --show-toplevel)
SCRIPTS_DIR="$REPO_ROOT/resources/christian.romney/skills/usage-insights/scripts"
```

All `clojure -M:<alias>` commands below are run with `cd "$SCRIPTS_DIR" &&`.
```

- [ ] **Step 2: Write Phase 1 — Setup & Collect**

```markdown
## Phase 1 — Setup & Collect

```bash
mkdir -p ~/.claude/usage-insights
```

Run collect-sessions and capture the JSON output:

```bash
cd "$SCRIPTS_DIR" && clojure -M:collect \
  --session-dir ~/.claude/usage-data/session-meta \
  --facets-dir  ~/.claude/usage-data/facets \
  --checkpoint  ~/.claude/usage-insights/checkpoint.json \
  $([ -n "$ARGUMENTS_SINCE" ] && echo "--since $ARGUMENTS_SINCE")
```

Parse the JSON output. Store:
- `new_sessions` — sessions not yet in the checkpoint
- `unfaceted_ids` — new sessions that lack a facet file
- `window_sessions` — sessions to include in the report narrative
```

- [ ] **Step 3: Write Phase 2 — Facet**

```markdown
## Phase 2 — Facet

For each session ID in `unfaceted_ids`:

1. Read `~/.claude/usage-data/session-meta/{session_id}.json`
2. Generate a facet using the prompt in @references/facet-prompt.md —
   replace `{SESSION_META_JSON}` with the raw JSON content.
3. Parse the model response as JSON and write it to
   `~/.claude/usage-data/facets/{session_id}.json`

Process sessions one at a time. Skip this phase if `unfaceted_ids` is empty.
```

- [ ] **Step 4: Write Phase 3 — Merge**

```markdown
## Phase 3 — Merge

Read the current config inventory:

```bash
cd "$SCRIPTS_DIR" && clojure -M:config --claude-dir ~/.claude
```

Store the JSON output as `CONFIG_SNAPSHOT`.

Merge all sessions from `new_sessions` into the checkpoint:

```bash
cd "$SCRIPTS_DIR" && clojure -M:merge \
  --checkpoint       ~/.claude/usage-insights/checkpoint.json \
  --session-meta-dir ~/.claude/usage-data/session-meta \
  --facets-dir       ~/.claude/usage-data/facets \
  --new-session-ids  {space-separated session IDs from new_sessions} \
  --config-snapshot  '{CONFIG_SNAPSHOT as JSON string}'
```

If `new_sessions` is empty, run the merge command without `--new-session-ids`
to refresh the config snapshot in the checkpoint.
```

- [ ] **Step 5: Write Phase 4 — Analyze**

```markdown
## Phase 4 — Analyze

Read `~/.claude/usage-insights/checkpoint.json`.

### Trend Classification

For each friction category key present in `weekly_buckets` across 3 or more
distinct weeks, classify the trajectory:

- **Declining** (↓): higher counts in the earlier half of weeks, near-zero recently
- **Persistent** (→): consistently present across the window
- **Emerging** (↑): low or absent early, increasing in recent weeks
- **Unclassified**: fewer than 3 weeks of data — omit the trend badge

For each classified category, compose a one-line trend note such as:
"5 instances in January, 0 since February."

### Config-Awareness

Read the `config_snapshot` from the checkpoint (hooks, skills, CLAUDE.md).
For each suggestion you plan to include in "Existing Features to Try",
classify it as one of:

- **Actionable**: not yet present in config
- **Already in place**: clear evidence in config (hook exists, skill installed,
  behavior documented in CLAUDE.md)
- **Partially addressed**: config exists but the suggestion extends beyond it

Store all classifications — they are used in Phase 5.
```

- [ ] **Step 6: Write Phase 5 — Render**

```markdown
## Phase 5 — Render

Synthesize the HTML report. Use `window_sessions` facets for the report
narrative; use the full checkpoint for trend analysis.

Follow the structure in @references/report-structure.md exactly —
same section IDs, same CSS class names.

Include all CSS from `report-structure.md` in the `<style>` block,
together with the full CSS from the existing `/insights` report.

### Metadata bar
Render immediately after `.subtitle`. Include:
- Date range: earliest to latest `start_time` in `window_sessions`
- Session count: number of sessions in `window_sessions`
- `--since {value}` only if `$ARGUMENTS.since` was provided
- `checkpoint: ~/.claude/usage-insights/checkpoint.json`

### Trend badges
For each friction card in `section-friction`, add the trend badge and
trend note from Phase 4. Omit if Unclassified.

### Config pills
For each feature card in `section-features`, add the config pill from
Phase 4. Apply `.muted` to cards with status "Already in place".

### Write output

```bash
TIMESTAMP=$(date -Iseconds)
```

Write the complete HTML to `~/.claude/usage-insights/report-${TIMESTAMP}.html`.
```

- [ ] **Step 7: Write Phase 6 — Confirm**

```markdown
## Phase 6 — Confirm

Print to the terminal:

```
Report written to: ~/.claude/usage-insights/report-{TIMESTAMP}.html
Sessions in scope: {count of window_sessions}
Checkpoint: ~/.claude/usage-insights/checkpoint.json ({count of analyzed_session_ids} total sessions analyzed)
```
```

- [ ] **Step 8: Commit**

```bash
git add resources/christian.romney/skills/usage-insights/SKILL.md
git commit -m "Write usage-insights SKILL.md orchestration body (6 phases)"
```

---

## Task 7: End-to-end validation

**Files:** None created — validation only.

- [ ] **Step 1: Run with no arguments**

```
/usage-insights
```

Expected:
- `~/.claude/usage-insights/` created
- `checkpoint.json` created
- `report-{timestamp}.html` created
- Terminal prints the confirmation block

- [ ] **Step 2: Open the report and check structure**

```bash
open ~/.claude/usage-insights/$(ls -t ~/.claude/usage-insights/*.html | head -1 | xargs basename)
```

Verify in browser:
- All 8 sections present with correct nav links
- Metadata bar visible below title
- At least one friction card with a trend badge
- At least one feature card with a config pill (expect "Already in place" for hooks/skills you have)

- [ ] **Step 3: Verify incrementality**

Run again immediately:

```
/usage-insights
```

Expected: second run produces no new facets, confirms the same total session count in `checkpoint.json`.

- [ ] **Step 4: Run with --since**

```
/usage-insights --since 14d
```

Expected:
- Report covers only last 14 days
- Metadata bar shows `--since 14d`
- `checkpoint.json` retains all previously analyzed sessions

- [ ] **Step 5: Commit any fixes found during validation**

```bash
git add -p
git commit -m "Fix issues found during usage-insights end-to-end validation"
```

---

## Self-Review

**Spec coverage:**

| Spec requirement | Covered by |
|---|---|
| `--since` filtering (date or relative duration) | Task 2 (`collect-sessions`), Task 6 Phase 1 |
| Incremental facet generation (skip existing) | Task 2 (`unfaceted_ids`), Task 6 Phase 2 |
| Checkpoint with weekly buckets | Task 3 (`merge-checkpoint`), Task 6 Phase 3 |
| Config inventory (CLAUDE.md, hooks, skills) | Task 4 (`read-config`), Task 6 Phase 3 |
| Trend classification (≥3 weeks, 3 categories) | Task 6 Phase 4 |
| Config-awareness (Actionable/In place/Partial) | Task 6 Phase 4 |
| HTML report matching existing structure | Task 6 Phase 5, Task 1 `report-structure.md` |
| Trend badges on friction cards | Task 6 Phase 5, Task 1 `report-structure.md` |
| Config pills on feature cards | Task 6 Phase 5, Task 1 `report-structure.md` |
| Report metadata bar | Task 6 Phase 5, Task 1 `report-structure.md` |
| Output to `~/.claude/usage-insights/` | Task 6 Phase 5 |
| Timestamped report filename | Task 6 Phase 5 |
| Skill scaffolded via /skill-creator | Task 5 |
| Terminal confirmation output | Task 6 Phase 6 |
| All tests pass before proceeding | Tasks 2–4 Step 4/5 |
| Friction taxonomy in facet prompt | Task 1 `facet-prompt.md` |
| Read-only on `~/.claude/usage-data/` | Task 6 Phases 1–3 (no writes to usage-data) |
