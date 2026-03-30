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
  "user_satisfaction_counts": {"<dominant signal — likely_satisfied | mixed | likely_frustrated>": <count>},
  "claude_helpfulness": "<one of: essential | helpful | limited | unhelpful>",
  "session_type": "<one of: multi_task | iterative_refinement | exploration | quick_question>",
  "friction_counts": {"<key from taxonomy below>": <integer>},
  "friction_detail": "<one sentence describing friction, or empty string>",
  "primary_success": "<one phrase describing the main thing that went well, or empty string>",
  "brief_summary": "<two sentences max summarizing the session>"
}

Note on user_satisfaction_counts: This is a count map. Use one key per distinct satisfaction signal observed. In most sessions there will be a single dominant signal, so a single-key map like {"likely_satisfied": 1} is typical. Use multiple keys only when the session clearly contains mixed signals.

Friction taxonomy — use only these keys, or add a new snake_case key if none fit:
- wrong_approach: Claude chose a method, tool, or strategy the user did not want
- user_rejected_action: Claude took an action the user interrupted or rolled back
- excessive_changes: Claude modified more than was asked
- misunderstood_request: Claude misread the user's intent
- buggy_code: Claude produced code with defects
- missed_requirement: Claude omitted part of the requested work
- external_tool_failure: An MCP server, plugin, or external tool failed

If there was no friction, set friction_counts to {} and friction_detail to "".

Goal category taxonomy — use the closest matching key from this list, or add a new snake_case key if none fit:
- code_implementation: writing, editing, or generating code
- debugging: investigating and fixing bugs or errors
- configuration: editing config files, settings, dotfiles, or CLAUDE.md
- documentation: writing or editing docs, READMEs, comments, or specs
- content_creation: writing non-code content (blog posts, reports, wiki pages, notes)
- file_management: organizing, moving, renaming, or deleting files
- information_retrieval: looking something up, research, reading, or Q&A
- tool_setup: installing or configuring tools, MCPs, hooks, or plugins
- design_exploration: brainstorming, architecture discussion, or planning
- knowledge_management: notes syncing, memory management, personal knowledge bases
- testing: writing or running tests, validation
- other: does not fit any category above
