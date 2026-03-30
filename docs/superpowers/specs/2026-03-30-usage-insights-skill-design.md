# usage-insights Skill Design

> **AI Disclosure**: Claude Sonnet 4.6 authored this document with Christian directing requirements and approving the design iteratively.
> **Last Review**: Reviewed by Christian Romney on 2026-Mar-30 (implementation update)
> **[Version History](#version-history)**

## 1. Overview

`/usage-insights` is a personal Claude Code skill that replaces the built-in `/insights` command with a time-aware, config-aware alternative. It addresses three documented limitations of `/insights`:

1. **Staleness** — resolved friction patterns continue to appear prominently because all sessions are weighted equally regardless of age
2. **Redundant suggestions** — features and workflows already in use are recommended anyway because the skill has no visibility into current configuration
3. **Synthesis cost scales with history** — the report synthesis step re-processes all sessions on every run

The replacement skill preserves the existing report structure and output format (HTML) while adding trend classification, config-awareness, and a checkpoint-driven incremental architecture.

---

## 2. Invocation

```
/usage-insights
/usage-insights --since 30d
/usage-insights --since 4w
/usage-insights --since 2026-02-01
```

**Arguments:**

| Argument | Type | Default | Description |
|---|---|---|---|
| `--since` | ISO date or relative duration | full history | Restrict report synthesis to sessions on or after this date. Checkpoint is always updated with all new sessions regardless. |

Relative duration units: `d` (days), `w` (weeks).

---

## 3. Output

All skill output is written to `~/.claude/usage-insights/`. This directory is entirely separate from `~/.claude/usage-data/`, which is Claude's internal data store and is treated as read-only by this skill.

```
~/.claude/usage-insights/
├── checkpoint.json          # updated in place on every run
└── report-{timestamp}.html  # new file per run (ISO 8601 timestamp)
```

Example report filename: `report-2026-03-30T14:23:00-07:00.html`

---

## 4. Architecture

The skill executes in six sequential phases:

```
session-meta/*.json ──► 1. Setup & Collect
                              │ (new sessions only)
                              ▼
                        2. Facet ◄── facets/*.json (reused)
                              │
                              ▼
                        3. Merge ◄── checkpoint.json
                              │
                              ▼
                        4. Analyze ◄── CLAUDE.md + hooks + skills
                              │
                              ▼
                        5. Render ──► report-{timestamp}.html
                                  └──► checkpoint.json (updated)
                              │
                              ▼
                        6. Confirm (print output path to terminal)
```

### Phase 1 — Setup & Collect

- Create `~/.claude/usage-insights/` if absent
- Load `checkpoint.json` if it exists; otherwise initialize an empty checkpoint
- Glob `~/.claude/usage-data/session-meta/*.json`
- Apply `--since` filter if provided
- Diff session IDs against `checkpoint.analyzed_session_ids` to identify new (unprocessed) sessions

The `collect-sessions` script outputs a compact JSON summary with three ID lists only — no session metadata. Full session objects are on disk and consumed directly by subsequent phases.

```json
{
  "new_sessions":    ["uuid1", "uuid2"],
  "unfaceted_ids":   ["uuid2"],
  "window_sessions": ["uuid1", "uuid2"]
}
```

### Phase 2 — Facet

For each new session that lacks a corresponding `~/.claude/usage-data/facets/{id}.json`, generate a facet using the established schema (see §5.2). Existing facets are reused without modification.

Sessions where `first_prompt` is absent or `"No prompt"` **and** `tool_counts` is empty are skipped — no facet is written. These are sessions that were opened and immediately closed, with no signal worth analyzing.

Facet generation uses a prompt that constrains friction category labels to the established taxonomy (see §5.3) to ensure stable aggregation keys.

### Phase 3 — Merge

Update `checkpoint.json`:
- Append new session IDs to `analyzed_session_ids`
- Bucket new sessions' facet data by ISO week into `weekly_buckets`
- Refresh `config_snapshot` from current config files

New session IDs are passed to the `merge-checkpoint` script as a single comma-separated quoted string (`--new-session-ids "id1,id2,..."`). This ensures the argument parser treats the ID list as one value and correctly processes all subsequent flags.

### Phase 4 — Analyze

Two sub-steps, both model-driven:

**Trend classification** — for each friction category with data in ≥3 distinct weeks, the model reviews the time-bucketed counts and classifies the trajectory as:
- **Declining** — present in earlier periods, near-zero recently
- **Persistent** — consistently present throughout the window
- **Emerging** — absent or low early, increasing recently
- Categories with fewer than 3 weeks of data are reported without classification

**Config-awareness** — the model reads the config inventory and reviews each generated suggestion, classifying it as:
- **Actionable** — not yet addressed in current config
- **Already in place** — config evidence exists
- **Partially addressed** — config exists but the suggestion extends beyond current configuration

### Phase 5 — Render

Synthesize and write the HTML report. The `--since` window determines which sessions contribute to the report narrative; the full checkpoint informs trend analysis regardless of window.

### Phase 6 — Confirm

Print the report path to the terminal.

---

## 5. Data Schemas

### 5.1 Checkpoint Schema

```json
{
  "version": 1,
  "last_updated": "2026-03-30T14:23:00Z",
  "analyzed_session_ids": ["uuid1", "uuid2"],
  "config_snapshot": {
    "claude_md_paths": ["~/.claude/CLAUDE.md"],
    "hooks": ["credential-guard.sh"],
    "skills": ["confluence", "generate-diagram", "usage-insights"]
  },
  "weekly_buckets": {
    "2026-W09": {
      "session_count": 12,
      "friction_counts": {
        "wrong_approach": 4,
        "user_rejected_action": 1
      },
      "goal_categories": {
        "configuration_editing": 5,
        "debugging_investigation": 3
      },
      "outcomes": {
        "mostly_achieved": 9,
        "partial": 2,
        "not_achieved": 1
      },
      "satisfaction": {
        "likely_satisfied": 9,
        "mixed": 2,
        "likely_frustrated": 1
      }
    }
  }
}
```

The schema is versioned (`"version": 1`) to support future migrations.

### 5.2 Facet Schema

Reuses the existing `~/.claude/usage-data/facets/*.json` schema:

```json
{
  "session_id": "uuid",
  "underlying_goal": "free text",
  "goal_categories": {"category_name": 1},
  "outcome": "mostly_achieved | partial | not_achieved",
  "user_satisfaction_counts": {"likely_satisfied": 1},
  "claude_helpfulness": "essential | helpful | limited | unhelpful",
  "session_type": "multi_task | iterative_refinement | exploration | quick_question",
  "friction_counts": {"friction_category": 1},
  "friction_detail": "free text",
  "primary_success": "string",
  "brief_summary": "free text"
}
```

### 5.3 Friction Taxonomy

Friction category labels are constrained to this taxonomy during facet generation. The taxonomy is open for extension — new categories may be added when observed behavior does not fit existing labels.

| Category | Description |
|---|---|
| `wrong_approach` | Claude chose a method, tool, or strategy the user did not want |
| `user_rejected_action` | Claude took an action the user interrupted or rolled back |
| `excessive_changes` | Claude modified more than was asked |
| `misunderstood_request` | Claude misread the user's intent |
| `buggy_code` | Claude produced code with defects |
| `missed_requirement` | Claude omitted part of the requested work |
| `external_tool_failure` | An MCP server, plugin, or external tool failed |

---

## 6. Config-Awareness Inputs

The skill reads the following to build the config inventory for suggestion classification:

| Source | What is extracted |
|---|---|
| `~/.claude/CLAUDE.md` | Documented tools, workflows, constraints, preferences |
| `~/.claude/hooks/*.sh` | Hook filenames (presence indicates hooks are configured) |
| `~/.claude/settings.json` | Hook entries under `hooks` key |
| `~/.claude/plugins/` | Installed skill names from plugin directory structure |

---

## 7. Report Structure

The report is structurally identical to the existing `/insights` `report.html` with three additions:

### 7.1 Preserved Sections

- At a Glance summary (with links to detail sections)
- Stats row (messages, lines, files, days, msgs/day)
- What You Work On (project areas with session counts)
- Charts (tools, languages, session types, response times, tool errors, multi-clauding)
- How You Use Claude Code (narrative + key insight)
- Impressive Things You Did
- Where Things Go Wrong
- Existing Features to Try
- New Usage Patterns
- On the Horizon
- Team Feedback

### 7.2 Additions

**Trend indicators on friction cards** — each card in "Where Things Go Wrong" gains:
- A trend badge: ↓ Declining / → Persistent / ↑ Emerging (omitted if insufficient data)
- A one-line trend note: e.g. "5 instances in January, 0 since February"

**Config status on feature suggestion cards** — each card in "Existing Features to Try" gains:
- A status pill: *Actionable* / *Already in place* / *Partially addressed*
- Cards marked "Already in place" render in a muted style

**Report metadata bar** — a strip below the title showing:
- Date range of sessions analyzed
- Session count in scope
- Whether `--since` was applied
- Checkpoint file used

---

## 8. Skill Implementation

| Property | Value |
|---|---|
| Skill name | `usage-insights` |
| Canonical source | [`resources/christian.romney/skills/usage-insights/`](../../resources/christian.romney/skills/usage-insights/) in this repo |
| Marketplace | [christianromney/claude-plugins](https://github.com/christianromney/claude-plugins) — install via `claude plugins install usage-insights@christianromney-claude-plugins` |
| Allowed tools | `Bash`, `Read`, `Write`, `Glob` |
| Scaffolding | Created via `/skill-creator` |

The skill ships three Clojure namespaces under `scripts/src/usage_insights/`:

| Script | Alias | Responsibility |
|---|---|---|
| `collect_sessions.clj` | `:collect` | Diff session-meta against checkpoint; return compact ID lists |
| `merge_checkpoint.clj` | `:merge` | Accumulate facet data into weekly buckets; refresh config snapshot |
| `read_config.clj` | `:config` | Inventory hooks, skills, and CLAUDE.md paths |

`deps.edn` lives at the skill root alongside `SKILL.md`, so all three scripts are invocable as `clojure -M:<alias>` without changing directory.

---

## 9. Design Principles Applied

- **Two-pass AI architecture**: structured extraction (facets, checkpoint) is separated from narrative synthesis (report). The checkpoint is the ground truth; HTML is a rendering artifact.
- **Open taxonomy**: friction and goal category keys are strings, not enums. Stability comes from prompt discipline, not hard schemas.
- **Model as logic**: trend classification and config-awareness are expressed as model prompts against structured data, not rule-based formulas.
- **Incrementalism at every layer**: facets are generated once per session; checkpoint synthesis costs only new sessions; `--since` windows can be applied without discarding prior analysis work.
- **Read-only on internal data**: `~/.claude/usage-data/` is never written to; all skill output lives in `~/.claude/usage-insights/`.

---

## Version History

| Date | Description | Changes | Review |
|---|---|---|---|
| 2026-Mar-30 | Initial design spec | +274 lines | Unreviewed |
| 2026-Mar-30 | Update AI disclosure to new format | +1 / -1 lines | Reviewed by Christian Romney on 2026-Mar-30 |
| 2026-Mar-30 | Update spec to reflect implementation: collect ID-only output, empty session skip rule, comma-separated IDs interface, Section 8 implementation details | +28 / -2 lines | Reviewed by Christian Romney on 2026-Mar-30 |
