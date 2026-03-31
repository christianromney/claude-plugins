---
name: usage-insights
description: >
  Generate a time-aware, config-aware HTML usage insight report from Claude Code
  session data. Improves on the built-in /insights command by adding trend
  classification (↓ Declining / → Persistent / ↑ Emerging) per friction
  category, config-awareness that suppresses suggestions already addressed in
  your CLAUDE.md, hooks, and installed skills, and checkpoint-driven incremental
  synthesis so report cost scales only with new sessions — not total history.
  Use this skill whenever the user invokes /usage-insights, asks for a usage
  report, or wants to understand trends in their Claude Code session history.
user_invocable: true
arguments:
  - name: since
    description: >
      Restrict the report narrative to sessions on or after this date or
      duration. Examples: 30d, 4w, 2026-02-01. Omit for full history.
      The checkpoint is always updated with all new sessions regardless.
    required: false
allowed-tools:
  - Bash
  - Read
  - Write
  - Glob
---

# usage-insights

Generate a time-aware, config-aware HTML usage insight report from Claude Code
session data. Output: `~/.claude/usage-insights/report-{timestamp}.html`.

Executes in six sequential phases. Complete all phases before printing the
confirmation message.

## Phase 1 — Setup & Collect

Create output directory if absent:

```bash
mkdir -p ~/.claude/usage-insights
```

Run collect-sessions and capture the JSON output:

```bash
clojure -M:collect \
  --session-dir ~/.claude/usage-data/session-meta \
  --facets-dir  ~/.claude/usage-data/facets \
  --checkpoint  ~/.claude/usage-insights/checkpoint.json \
  $([ -n "$ARGUMENTS_SINCE" ] && echo "--since $ARGUMENTS_SINCE")
```

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

---

## Phase 2 — Facet

For each session ID in `unfaceted_ids`:

1. Read `~/.claude/usage-data/session-meta/{session_id}.json`
2. If `first_prompt` is absent or `"No prompt"` **and** `tool_counts` is empty,
   skip this session — do not write a facet file.
3. Otherwise, generate a facet using the prompt in @references/facet-prompt.md —
   replace `{SESSION_META_JSON}` with the raw JSON content.
4. Parse the model response as JSON and write it to
   `~/.claude/usage-data/facets/{session_id}.json`

Process sessions one at a time. Skip this phase entirely if `unfaceted_ids` is empty.

---

## Phase 3 — Merge

Read the current config inventory:

```bash
clojure -M:config --claude-dir ~/.claude
```

Store the JSON output as `CONFIG_SNAPSHOT`.

Merge all sessions from `new_sessions` into the checkpoint:

```bash
clojure -M:merge \
  --checkpoint         ~/.claude/usage-insights/checkpoint.json \
  --session-meta-dir   ~/.claude/usage-data/session-meta \
  --facets-dir         ~/.claude/usage-data/facets \
  --new-session-ids    "{comma-separated session IDs from new_sessions}" \
  --config-snapshot    '{CONFIG_SNAPSHOT as JSON string}' \
  --ignored-session-ids "{comma-separated IDs from confirmed_ignore_ids}"
```

If `new_sessions` is empty, omit `--new-session-ids`.
If `confirmed_ignore_ids` is empty, omit `--ignored-session-ids`.

---

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

---

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

---

## Phase 6 — Confirm

Print to the terminal:

```
Report written to: ~/.claude/usage-insights/report-{TIMESTAMP}.html
Sessions in scope: {count of window_sessions}
Checkpoint: ~/.claude/usage-insights/checkpoint.json ({count of analyzed_session_ids} total sessions analyzed)
Note: the current session will be included in the next run.
```
