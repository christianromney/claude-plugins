---
name: sync:dotfiles
description: Sync dotfiles bidirectionally between home directory and remote repository using chezmoi
user_invocable: true
---

# Sync Dotfiles with Chezmoi

Sync the user's dotfiles bidirectionally using chezmoi — pull remote changes
from another machine and push local changes to the remote. The user's chezmoi
configuration has `git.autoCommit` and `git.autoPush` enabled, so local-to-remote
sync is automatic after each re-add.

Follow these steps carefully:

## 0. Pull Remote Changes

Pull any changes pushed from another machine:

```bash
chezmoi update --apply=false
```

This fetches and merges remote commits into the source dir without touching
the home directory. After this, `chezmoi status` will show `M ` for any files
the remote changed that differ from home.

If the pull fails (network, auth), report the error and ask whether to
continue with local-only sync.

## 1. Re-add Session Edits

Before checking status, review what files in `~/.claude/` were written or edited
during this session. For each such file, run `chezmoi re-add <file>` now (using
the full path). This replaces the PostToolUse hook that previously did this
automatically on every Edit/Write.

Skip this step if no `~/.claude/` files were touched this session.

## 2. Check Status

Run `chezmoi status` and interpret the two-column output for the user:

| Status | Meaning | Action needed |
|--------|---------|---------------|
| ` M`   | Home dir changed, source unchanged | Sync home → source |
| `M `   | Source changed, home unchanged | Apply source → home |
| `MM`   | Both changed | Conflict — ask user which wins |

If no files are modified, inform the user that the repository is already synced
and stop.

## 3. Show Differences

Run `chezmoi diff` to display what changed. The diff is displayed via delta in
side-by-side mode. The orientation is:
- Left side (red/minus) = what is currently in the home directory
- Right side (green/plus) = what chezmoi would apply (source/target state)

This is standard diff convention: left = current (old), right = new. Do not
invert this — confusing the sides will cause you to misidentify which changes
belong to source vs. home.

## 4. Ask for Confirmation

Ask the user if they want to proceed with syncing these changes. For any `MM`
(conflict) files, enumerate the specific unique changes on each side (not just
"source" vs "home") and ask the user what the desired final value should be for
each change. Do not ask which side "wins" wholesale — the user needs to see
exactly what will be discarded before confirming. `chezmoi apply --force` is
irreversible once autoCommit pushes it.

## 5. Sync Changes

For each modified file, select the appropriate command based on its source
attributes. Use `chezmoi source-path <file>` to get the source filename, then
inspect it:

| File attributes | Command |
|-----------------|---------|
| Non-template, not encrypted | `chezmoi re-add <file>` |
| Non-template, encrypted (source starts with `encrypted_`) | `chezmoi re-add <file>` |
| Template (source ends in `.tmpl`), not encrypted | `chezmoi add --template <file>` |
| Template, encrypted | `chezmoi add --template --encrypt <file>` |

For ` M` files (home changed, source unchanged — push):
- Use the appropriate `add` command from the table above

For `M ` files (source changed, home unchanged — pulled from remote):
- `chezmoi apply --force <file>`

For `MM` conflict files:
- **Source wins** → `chezmoi apply --force <file>`
- **Home dir wins** → use the appropriate `add` command from the table above

## 6. Verify and Confirm

Run `chezmoi status` again after syncing. If any files are still listed as
modified, `chezmoi re-add` likely silently skipped a template file — re-run
using `chezmoi add --template <file>` for those files.

Once status is clean, confirm to the user that the sync completed successfully.

## Error Handling

**Silent no-op (status still dirty after re-add):** The file is a template that
`re-add` skipped without warning. Use `chezmoi add --template <file>` instead.

**TTY error on `MM` files:** `chezmoi apply` on a conflicted file tries to open
an interactive merge. If you see `could not open a new TTY`, use
`chezmoi apply --force <file>` instead.

**Network/auth errors:** Check:
- Remote is accessible
- SSH keys are configured
- Network connection is working
- Auto-commit/auto-push settings in `~/.config/chezmoi/chezmoi.toml`

## Known Template Files

These files are templates and must always be synced with `chezmoi add --template <file>`.
Do not use `chezmoi re-add` for these files — it will silently do nothing.

| Home path | Source path |
|-----------|-------------|
| `~/.gitconfig` | `dot_gitconfig.tmpl` |
| `~/.lein/profiles.clj` | `dot_lein/symlink_profiles.clj.tmpl` |
| `~/.config/fish/config.fish` | `private_dot_config/private_fish/config.fish.tmpl` |
| `~/.config/fish/fish_variables` | `private_dot_config/private_fish/fish_variables.tmpl` |
| `~/.config/homebrew/Brewfile` | `private_dot_config/homebrew/Brewfile.tmpl` |
| `~/.config/jj/config.toml` | `private_dot_config/private_jj/config.toml.tmpl` |

To rediscover this list at any time: `find ~/.local/share/chezmoi -name "*.tmpl"`

## Important Notes

- `chezmoi re-add` silently skips template files (`.tmpl`) — always verify
  status is clean after running it
- `chezmoi re-add` preserves encryption attributes automatically; `chezmoi add`
  requires `--encrypt` to be passed explicitly
- Auto-commit and auto-push are enabled in the user's configuration
- Commit messages are auto-generated by chezmoi
