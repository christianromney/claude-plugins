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
