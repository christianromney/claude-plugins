---
name: sync:notes
description: Sync personal-notes and/or work-notes repositories to GitHub using jujutsu
user_invocable: true
---

# Sync Notes Repositories to GitHub

Syncs one or both notes repositories to GitHub using jujutsu (jj).

## Repositories

- **personal**: `$HOME/Documents/personal/notes` → `git@github.com:christianromney/personal-notes.git`
- **work**: `$HOME/Documents/work/notes` → `git@github.com:christianromney/work-notes.git`

## Argument

Optional positional argument:

- `personal` — sync the personal-notes repo only
- `work` — sync the work-notes repo only
- `both` (default) — sync both repos sequentially

## Steps

For each target repo, perform the steps below. When syncing `both`, run the full sequence on `personal` first, then on `work`. Each repo is independent — failures in one do not abort the other (just report the error and continue).

### 1. Change to Repository

Navigate to the target repo:

```bash
cd "$REPO_DIR"
```

Where `$REPO_DIR` is `$HOME/Documents/personal/notes` for `personal` or `$HOME/Documents/work/notes` for `work`.

If the directory doesn't exist, inform the user and skip this repo.

### 2. Fetch Remote Changes

Always fetch latest changes from GitHub:

```bash
jj git fetch
```

This updates the view of remote branches without modifying local work.

### 3. Check Working Copy Status

```bash
jj status
```

### 4. Handle Changes

#### If Working Copy Has Changes:

**a) Summarize Changes**

```bash
jj diff --stat
jj status
```

**b) Generate and Present Description**

Review the output and generate an appropriate commit message.

- Include bulleted details for multi-part changes
- Be specific about what changed (e.g., "Update journal entries for October")

Show the user the proposed description, explain what changes you found, ask for approval or modification, and wait for confirmation.

**c) Apply Description**

Once approved, apply via heredoc:

```bash
jj describe -m "$(cat <<'EOF'
<approved description here>
EOF
)"
```

**d) Check if Rebase is Needed**

```bash
jj log -r '@..main@origin'
```

If non-empty, remote moved ahead. Rebase:

```bash
jj rebase -d main@origin
```

**e) Update Bookmark**

```bash
jj bookmark set main -r @
```

**f) Push to GitHub**

```bash
jj git push --bookmark main
```

**g) Report Success**

```bash
jj log --limit 3
```

#### If Working Copy Has No Changes:

**a) Check for committed-but-unpushed changes**

```bash
jj log -r 'main..@-'
```

- **If non-empty**: rebase if needed, move bookmark to `@-`, push.
  1. `jj log -r '@-..main@origin'` — check remote ahead
  2. `jj rebase -d main@origin` if remote ahead
  3. `jj bookmark set main -r @-`
  4. `jj git push --bookmark main`
  5. Report success with `jj log --limit 3`

- **If empty**: check `jj log -r 'main@origin..main'`.
  - **If non-empty** (remote moved): fast-forward bookmark with `jj bookmark set main -r main@origin`, report.
  - **If empty**: report this repo is already up to date.

### 5. Error Handling

- **Directory not found**: skip that repo, inform user, continue to next if syncing `both`
- **Push fails**: show error; suggest checking remote, SSH keys, network
- **No remote configured**: inform user to set up origin with `jj git remote add origin <url>`

## Important Notes

- Use `$HOME` for portability across machines
- Always present descriptions for user approval before applying
- Rebase is triggered when remote has new commits the working copy is not based on
- After push, jj automatically creates a new empty working copy on top
- The skill works from any cwd — it changes to each target repo

## Example Output

When syncing `both`:

```
[personal] Fetched remote
[personal] Working copy clean; up to date
[work] Fetched remote
[work] Working copy has changes
[work] Generated description (presented for approval)
[work] Applied description
[work] Pushed to GitHub

Synced: 0 personal, 1 work
```
