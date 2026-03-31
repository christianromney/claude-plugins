# claude-plugins

Personal Claude Code plugin marketplace for [Christian Romney](https://github.com/christianromney).

This repository is my personal skill marketplace — a home for skills tailored to my own
preferences, tools, and workflows. It also serves as an incubator: skills that prove broadly
useful may eventually be contributed upstream to the community.

Install the marketplace with:

```bash
claude plugins add https://github.com/christianromney/claude-plugins
```

Then install individual plugins:

```bash
claude plugins install <plugin-name>@christianromney-claude-plugins
```

## Plugins and Skills

| Plugin | Skill | Category | Description |
|--------|-------|----------|-------------|
| [chezmoi](chezmoi/) | `chezmoi:chezmoi` | Reference | Comprehensive guide to chezmoi's three-state model, source attributes, symlink patterns, templates, and scripts |
| [generate](generate/) | `generate:arch-briefing` | Generator | Produce a structured architectural briefing document for a given codebase or project |
| [generate](generate/) | `generate:commit-message` | Generator | Draft a commit message focused on intent and rationale rather than mechanics |
| [generate](generate/) | `generate:diagram` | Generator | Create a Mermaid diagram (DFD, ER, sequence, state, or component) following consistent conventions |
| [journal](journal/) | `journal:blog` | Publishing | Write and publish a blog post to basic-memory |
| [journal](journal/) | `journal:slack` | Publishing | Post a journal entry to the `#christian-ai-journal` Slack channel |
| [jujutsu](jujutsu/) | `jujutsu:jujutsu` | Reference | Guide to Jujutsu (jj) — working-copy model, bookmarks, common workflows, and Git translation table |
| [socratic-partner](socratic-partner/) | `socratic-partner:socratic-partner` | Persona | Structured problem-solving partner: refines problem statements, surfaces evaluation criteria, and builds decision matrices via Socratic dialogue |
| [sync](sync/) | `sync:dotfiles` | Workflow | Sync home directory changes back to the chezmoi source repository, handling templates and conflicts |
| [sync](sync/) | `sync:notes` | Workflow | Sync the personal notes repository to GitHub using jujutsu, including fetch, rebase, and push |
| [usage-insights](usage-insights/) | `usage-insights:usage-insights` | Analytics | Generate a time-aware, config-aware HTML usage report from Claude Code session data with trend classification |

### Categories

| Category | Description |
|----------|-------------|
| **Reference** | Loads knowledge about a tool or system so Claude can apply it accurately in context |
| **Generator** | Produces a specific artifact (document, diagram, message) following a defined format |
| **Publishing** | Publishes content to an external destination |
| **Workflow** | Guides execution of a multi-step operational task |
| **Persona** | Establishes a collaborative mode or thinking style for a session |
| **Analytics** | Collects, analyzes, and reports on data |
