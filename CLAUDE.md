# Claude Plugins (christianromney-claude-plugins)

This is Christian Romney's personal Claude Code plugins repository, organized as a marketplace.

## Repository Structure

- One directory per plugin at the repo root
- `.claude-plugin/marketplace.json` — marketplace manifest read by the Discover UI
- Each plugin's `<plugin>/.claude-plugin/plugin.json` — the plugin's own manifest

## Versioning

When bumping a plugin version, **both files must be updated in the same commit**:

1. `<plugin>/.claude-plugin/plugin.json` — the plugin's own `version` field
2. `.claude-plugin/marketplace.json` — the corresponding `plugins[].version` entry

The Discover UI in `/plugin` reads from `marketplace.json`. If only `plugin.json` is updated, the Discover tab shows the wrong (old) version.

These two files are separate sources of truth that must agree.

### After pushing a version bump

```
claude plugins marketplace update christianromney-claude-plugins
claude plugins update <plugin>@christianromney-claude-plugins
```
