# usage-insights

Generate time-aware, config-aware HTML usage insight reports from Claude Code session data.

Improves on the built-in `/insights` command by adding:
- **Trend classification** — each friction category is classified as ↓ Declining, → Persistent, or ↑ Emerging based on weekly session data
- **Config-awareness** — suggestions already addressed in your CLAUDE.md, hooks, or installed skills are marked "Already in place"
- **Incremental synthesis** — a checkpoint tracks analyzed sessions so report cost scales only with new sessions, not total history

## Skills

| Skill | Invocation | Purpose |
|---|---|---|
| [usage-insights](skills/usage-insights/SKILL.md) | `/usage-insights [--since <date\|duration>]` | Generate a usage insight report |

## Dependencies

Requires [Clojure](https://clojure.org/guides/install_clojure) and [tools.deps](https://clojure.org/guides/deps_and_cli) (`clojure` CLI).
