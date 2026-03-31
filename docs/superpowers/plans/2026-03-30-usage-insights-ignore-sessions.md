# usage-insights: Ignore Sessions & Short-Circuit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add corrupted-session detection with user-prompted ignore-listing, a no-op short-circuit when nothing has changed, and a current-session visibility note to the usage-insights skill.

**Architecture:** Three independent changes: (1) `collect-sessions` reports unparseable files in a new `ignore_sessions` output key and silently skips already-ignored IDs; (2) `merge-checkpoint` stores confirmed ignore IDs in `ignored_session_ids`; (3) `SKILL.md` orchestrates the user prompt, short-circuit logic, and confirmation note.

**Tech Stack:** Clojure 1.12, cheshire JSON, cognitect test-runner, Bash/jq in SKILL.md

---

## File Map

| File | Change |
|------|--------|
| `scripts/src/usage_insights/collect_sessions.clj` | `load-sessions` accepts `ignored-ids`; returns `{:sessions :ignore_sessions}`; `collect` reads `ignored_session_ids` from checkpoint and includes `:ignore_sessions` in output |
| `scripts/src/usage_insights/merge_checkpoint.clj` | `merge-into-checkpoint` accepts `ignored-session-ids`; stores them in checkpoint under `ignored_session_ids`; `-main` parses `--ignored-session-ids` flag |
| `scripts/test/usage_insights/collect_sessions_test.clj` | Update `load-sessions` callers for new signature; add 4 new tests |
| `scripts/test/usage_insights/merge_checkpoint_test.clj` | Add 3 new tests for `ignored_session_ids` storage |
| `skills/usage-insights/SKILL.md` | Phase 1: add short-circuit + ignore prompts; Phase 3: add `--ignored-session-ids` flag; Phase 6: add current-session note |

---

## Task 1: `ignore_sessions` in collect-sessions

**Files:**
- Modify: `usage-insights/skills/usage-insights/scripts/src/usage_insights/collect_sessions.clj`
- Modify: `usage-insights/skills/usage-insights/scripts/test/usage_insights/collect_sessions_test.clj`

- [ ] **Step 1: Write the failing tests**

Add to `collect_sessions_test.clj`:

```clojure
(defn- write-corrupt-json [path]
  (io/make-parents path)
  (spit path "{ \"session_id\": \"corrupt\"\u0000 }"))  ; null byte

(deftest load-sessions-returns-ignore-sessions-for-corrupt-files
  (let [dir (tmp-dir)]
    (make-session dir "good-id" "2026-03-01T10:00:00Z")
    (write-corrupt-json (str dir "/session-meta/bad-id.json"))
    (let [{:keys [sessions ignore_sessions]}
          (sut/load-sessions (str dir "/session-meta") #{})]
      (is (= 1 (count sessions)))
      (is (= ["bad-id"] ignore_sessions)))))

(deftest load-sessions-silently-skips-already-ignored-ids
  (let [dir (tmp-dir)]
    (write-corrupt-json (str dir "/session-meta/bad-id.json"))
    (let [result (with-out-str
                   (binding [*err* *out*]
                     (sut/load-sessions (str dir "/session-meta") #{"bad-id"})))]
      ;; No WARNING should be emitted for already-ignored file
      (is (not (.contains result "WARNING"))))))

(deftest collect-includes-ignore-sessions-in-output
  (let [dir (tmp-dir)]
    (make-session dir "good-id" "2026-03-01T10:00:00Z")
    (write-corrupt-json (str dir "/session-meta/bad-id.json"))
    (let [checkpoint {:version 1 :analyzed_session_ids [] :ignored_session_ids []
                      :weekly_buckets {}}
          result (sut/collect (str dir "/session-meta")
                              (str dir "/facets")
                              checkpoint nil)]
      (is (= ["bad-id"] (:ignore_sessions result))))))

(deftest collect-skips-sessions-in-ignored-session-ids
  (let [dir (tmp-dir)]
    (write-corrupt-json (str dir "/session-meta/bad-id.json"))
    (let [checkpoint {:version 1 :analyzed_session_ids [] :ignored_session_ids ["bad-id"]
                      :weekly_buckets {}}
          result (sut/collect (str dir "/session-meta")
                              (str dir "/facets")
                              checkpoint nil)]
      (is (= [] (:ignore_sessions result)))
      (is (= [] (:new_sessions result))))))
```

Also update the existing `load-sessions-reads-all-json` test to pass the new second argument and destructure the new return shape:

```clojure
(deftest load-sessions-reads-all-json
  (let [dir (tmp-dir)]
    (make-session dir "id-1" "2026-03-01T10:00:00Z")
    (make-session dir "id-2" "2026-03-02T10:00:00Z")
    (let [{:keys [sessions]} (sut/load-sessions (str dir "/session-meta") #{})]
      (is (= 2 (count sessions)))
      (is (= #{"id-1" "id-2"} (set (map :session_id sessions)))))))
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd ~/.claude/plugins/marketplaces/christianromney-claude-plugins/usage-insights/skills/usage-insights
clojure -M:test 2>&1 | tail -20
```

Expected: failures on the 4 new tests; existing `load-sessions-reads-all-json` also fails (wrong arity).

- [ ] **Step 3: Update `load-sessions` to return `{:sessions :ignore_sessions}` and accept `ignored-ids`**

Replace the existing `load-sessions` function:

```clojure
(defn load-sessions
  "Read all session-meta JSON files from session-dir.
   Silently skips files whose ID appears in ignored-ids.
   Returns {:sessions [...] :ignore_sessions [...]} where :ignore_sessions
   contains IDs of files that failed to parse and are not already ignored."
  [session-dir ignored-ids]
  (let [dir (io/file session-dir)]
    (if (.isDirectory dir)
      (reduce
       (fn [{:keys [sessions ignore_sessions]} f]
         (let [id (subs (.getName f) 0 (- (count (.getName f)) 5))]
           (cond
             (contains? ignored-ids id)
             {:sessions sessions :ignore_sessions ignore_sessions}

             :else
             (try
               {:sessions (conj sessions (json/parse-string (slurp f) true))
                :ignore_sessions ignore_sessions}
               (catch Exception e
                 (binding [*out* *err*]
                   (println (str "WARNING: failed to parse session file "
                                 (.getName f) ": " (.getMessage e))))
                 {:sessions sessions
                  :ignore_sessions (conj ignore_sessions id)})))))
       {:sessions [] :ignore_sessions []}
       (->> (.listFiles dir)
            (filter #(.. % getName (endsWith ".json")))))
      {:sessions [] :ignore_sessions []})))
```

- [ ] **Step 4: Update `collect` to use the new `load-sessions` signature and include `:ignore_sessions`**

Replace the existing `collect` function:

```clojure
(defn collect
  "Return {:new_sessions [...] :unfaceted_ids [...] :window_sessions [...] :ignore_sessions [...]}.
   new_sessions: not yet in checkpoint.
   unfaceted_ids: new sessions without an existing facet file.
   window_sessions: all sessions on or after since (or all if since is nil).
   ignore_sessions: session IDs that failed to parse (not already in ignored_session_ids)."
  [session-dir facets-dir checkpoint since]
  (let [analyzed-ids  (set (:analyzed_session_ids checkpoint))
        ignored-ids   (set (:ignored_session_ids checkpoint))
        {:keys [sessions ignore_sessions]} (load-sessions session-dir ignored-ids)
        new-sessions  (remove #(analyzed-ids (:session_id %)) sessions)
        unfaceted-ids (->> new-sessions
                           (remove #(.exists (io/file facets-dir
                                                      (str (:session_id %) ".json"))))
                           (mapv :session_id))
        window-sessions (if since
                          (filterv #(.isAfter (Instant/parse (:start_time %)) since)
                                   sessions)
                          (vec sessions))]
    {:new_sessions    (mapv :session_id new-sessions)
     :unfaceted_ids   unfaceted-ids
     :window_sessions (mapv :session_id window-sessions)
     :ignore_sessions ignore_sessions}))
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
cd ~/.claude/plugins/marketplaces/christianromney-claude-plugins/usage-insights/skills/usage-insights
clojure -M:test 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
cd /Users/christian.romney/dev/nu/claude-plugins
git add usage-insights/skills/usage-insights/scripts/src/usage_insights/collect_sessions.clj
git add usage-insights/skills/usage-insights/scripts/test/usage_insights/collect_sessions_test.clj
git commit -m "Add ignore_sessions to collect output; skip already-ignored files silently"
```

---

## Task 2: `ignored_session_ids` in merge-checkpoint

**Files:**
- Modify: `usage-insights/skills/usage-insights/scripts/src/usage_insights/merge_checkpoint.clj`
- Modify: `usage-insights/skills/usage-insights/scripts/test/usage_insights/merge_checkpoint_test.clj`

- [ ] **Step 1: Write the failing tests**

Add to `merge_checkpoint_test.clj`:

```clojure
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
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd ~/.claude/plugins/marketplaces/christianromney-claude-plugins/usage-insights/skills/usage-insights
clojure -M:test 2>&1 | tail -20
```

Expected: 3 new test failures (wrong arity on `merge-into-checkpoint`).

- [ ] **Step 3: Update `load-checkpoint` default and `merge-into-checkpoint` signature**

Update `load-checkpoint` to include `ignored_session_ids` in the default:

```clojure
(defn- load-checkpoint [path]
  (let [f (io/file path)]
    (if (.exists f)
      (json/parse-string (slurp f) true)
      {:version 1 :last_updated "" :analyzed_session_ids []
       :ignored_session_ids [] :config_snapshot {} :weekly_buckets {}})))
```

Update `merge-into-checkpoint` to accept and store `ignored-session-ids`:

```clojure
(defn merge-into-checkpoint
  "Read checkpoint at path, merge new-session-ids and ignored-session-ids, write result back.
   Returns updated checkpoint."
  [checkpoint-path session-meta-dir facets-dir new-session-ids ignored-session-ids config-snapshot]
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
                          (assoc :last_updated (str (Instant/now)))
                          (update :ignored_session_ids
                                  #(vec (distinct (concat (or % []) ignored-session-ids)))))
                      new-session-ids)]
    (.mkdirs (.getParentFile (io/file checkpoint-path)))
    (spit checkpoint-path (json/generate-string updated {:pretty true}))
    updated))
```

- [ ] **Step 4: Update `-main` to parse `--ignored-session-ids`**

Replace `-main`:

```clojure
(defn -main [& args]
  (let [opts          (parse-args args)
        ids           (when-let [s (get opts "--new-session-ids")]
                        (mapv clojure.string/trim (clojure.string/split s #",")))
        ignored-ids   (when-let [s (get opts "--ignored-session-ids")]
                        (mapv clojure.string/trim (clojure.string/split s #",")))
        snapshot      (json/parse-string (get opts "--config-snapshot" "{}") true)
        updated       (merge-into-checkpoint
                       (get opts "--checkpoint")
                       (get opts "--session-meta-dir")
                       (get opts "--facets-dir")
                       (or ids [])
                       (or ignored-ids [])
                       snapshot)]
    (println (str "Checkpoint updated: "
                  (count (:analyzed_session_ids updated))
                  " total sessions analyzed"))))
```

- [ ] **Step 5: Update existing tests that call `merge-into-checkpoint` with the old 5-arg signature**

All existing calls pass `[]` as the new `ignored-session-ids` argument. Update each one:

```clojure
;; Every call site: add [] after new-session-ids argument
(sut/merge-into-checkpoint cp dir/session-meta dir/facets ["s1"] [] snapshot)
;;                                                                  ^^
```

Affected tests: `merge-creates-checkpoint-when-missing`, `merge-skips-already-analyzed`, `merge-handles-session-without-facet`, `parse-args-preserves-config-snapshot-after-many-ids`, `merge-updates-config-snapshot`.

- [ ] **Step 6: Run all tests to verify they pass**

```bash
cd ~/.claude/plugins/marketplaces/christianromney-claude-plugins/usage-insights/skills/usage-insights
clojure -M:test 2>&1 | tail -20
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
cd /Users/christian.romney/dev/nu/claude-plugins
git add usage-insights/skills/usage-insights/scripts/src/usage_insights/merge_checkpoint.clj
git add usage-insights/skills/usage-insights/scripts/test/usage_insights/merge_checkpoint_test.clj
git commit -m "Store ignored_session_ids in checkpoint; accept --ignored-session-ids in merge"
```

---

## Task 3: SKILL.md — short-circuit, ignore prompts, session note

**Files:**
- Modify: `usage-insights/skills/usage-insights/SKILL.md`

- [ ] **Step 1: Update Phase 1 to store `ignore_sessions` and add short-circuit logic**

Replace the "Parse the JSON output" block at the end of Phase 1 with:

```markdown
Parse the JSON output. Store:
- `new_sessions` — sessions not yet in the checkpoint
- `unfaceted_ids` — new sessions that lack a facet file
- `window_sessions` — sessions to include in the report narrative
- `ignore_sessions` — session IDs that failed to parse

**Short-circuit check:** If `new_sessions` is empty **and** `ignore_sessions` is empty:

1. Read the fresh config:
   ```bash
   clojure -M:config --claude-dir ~/.claude
   ```
2. Read the stored config snapshot from the checkpoint:
   ```bash
   jq -c '.config_snapshot' ~/.claude/usage-insights/checkpoint.json
   ```
3. Compare the two JSON objects. If they are semantically identical, print:
   ```
   No new sessions or config changes since last report.
   Most recent report: ~/.claude/usage-insights/report-{most recent filename}.
   Note: the current session will be included in the next run.
   ```
   Then stop — do not proceed to Phase 2.

**Ignore prompt:** For each ID in `ignore_sessions`, ask the user:

> Session `{id}` appears corrupted. Ignore it and continue?

- If **yes**: add the ID to a `confirmed_ignore_ids` list. Inform the user:
  "Added `{id}` to the ignore list — it will be skipped on all future runs."
- If **no**: print "Aborting analysis." and stop.

Proceed to Phase 2 only after all `ignore_sessions` prompts are resolved.
```

- [ ] **Step 2: Update Phase 3 to pass `--ignored-session-ids` when present**

Replace the merge command block with:

```markdown
Merge all sessions from `new_sessions` into the checkpoint:

```bash
clojure -M:merge \
  --checkpoint       ~/.claude/usage-insights/checkpoint.json \
  --session-meta-dir ~/.claude/usage-data/session-meta \
  --facets-dir       ~/.claude/usage-data/facets \
  --new-session-ids  "{comma-separated session IDs from new_sessions}" \
  --config-snapshot  '{CONFIG_SNAPSHOT as JSON string}' \
  $([ -n "$CONFIRMED_IGNORE_IDS" ] && echo "--ignored-session-ids $CONFIRMED_IGNORE_IDS")
```

If `new_sessions` is empty, run the merge command without `--new-session-ids`.
If `confirmed_ignore_ids` is empty, omit `--ignored-session-ids`.
```

- [ ] **Step 3: Update Phase 6 confirmation to add current-session note**

Replace the Phase 6 print block with:

```markdown
Print to the terminal:

```
Report written to: ~/.claude/usage-insights/report-{TIMESTAMP}.html
Sessions in scope: {count of window_sessions}
Checkpoint: ~/.claude/usage-insights/checkpoint.json ({count of analyzed_session_ids} total sessions analyzed)
Note: the current session will be included in the next run.
```
```

- [ ] **Step 4: Commit**

```bash
cd /Users/christian.romney/dev/nu/claude-plugins
git add usage-insights/skills/usage-insights/SKILL.md
git commit -m "Add short-circuit, ignore prompts, and current-session note to usage-insights skill"
```

---

## Task 4: Reinstall and verify

- [ ] **Step 1: Push and reinstall the plugin**

```bash
cd /Users/christian.romney/dev/nu/claude-plugins
git push
claude plugins marketplace update christianromney-claude-plugins
claude plugins install usage-insights@christianromney-claude-plugins
```

- [ ] **Step 2: Run `/usage-insights` and verify the short-circuit fires**

Since the session data and config are currently unchanged, the skill should print the short-circuit message and stop without generating a new report.

- [ ] **Step 3: Verify the ignore prompt fires on the corrupt file**

The corrupt file `7e8f5372-...` should be detected and reported in `ignore_sessions`. The skill should ask whether to ignore it. Answer yes. Verify the ID appears in `ignored_session_ids` in the checkpoint:

```bash
jq '.ignored_session_ids' ~/.claude/usage-insights/checkpoint.json
```

Expected: `["7e8f5372-2f79-457c-981e-621105b7cf2c"]`

- [ ] **Step 4: Run `/usage-insights` again and verify the corrupt file warning is gone**

On the next run, the corrupt file should be silently skipped.
