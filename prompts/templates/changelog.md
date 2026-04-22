# Changelog Generation

Generate a changelog from the git log between two refs.

## Step 1 — collect commits

Run this command (hardcode `FROM` and `TO` while experimenting, then replace with variables):

```bash
# Hardcoded (start here)
git log "v0.1.0..v0.2.0" --pretty=format:"%s%n%b" --no-merges

# Parameterized (once you know it works)
git log "$FROM..$TO" --pretty=format:"%s%n%b" --no-merges
```

## Step 2 — raw commit log

Paste the output below:

```
[[GIT_LOG]]
```

Parameters used:

| Variable | Value             |
|----------|-------------------|
| FROM     | [[FROM]]          |
| TO       | [[TO]]            |

## Output format

Use [Keep a Changelog](https://keepachangelog.com) structure.
Group entries under the sections below **in this order**, omitting any section that has no entries.

```
## [[[TO]]] — YYYY-MM-DD

### Features
- <subject line, imperative>

### Bug Fixes
- <subject line, imperative>

### Refactoring
- <subject line, imperative>

### Chores
- <subject line, imperative>
```

### Mapping — Conventional Commit type → section

| Commit type        | Changelog section |
|--------------------|-------------------|
| `feat`             | Features          |
| `fix`              | Bug Fixes         |
| `refactor`         | Refactoring       |
| `chore`, `ci`, `build`, `deps` | Chores |
| `docs`, `style`, `test`, `perf` | (omit unless BREAKING CHANGE) |

### BREAKING CHANGE rule

If a commit footer contains `BREAKING CHANGE:`, prepend the entry with **`⚠ BREAKING CHANGE:`** in bold and place it at the top of its section, above non-breaking entries.

Example:
```
### Features
- **⚠ BREAKING CHANGE:** rename `/dogs/match` endpoint to `/matches` — update all API clients
- add compatibility score to match response
```

## Rules

- One bullet per commit; use the subject line as the bullet text.
- Strip the `type(scope):` prefix — keep only the description.
- Imperative mood, lowercase first word, no trailing period.
- If a commit references an issue (`#N` or `Closes #N`), append ` (#N)` to the bullet.
- Do NOT include merge commits or version-bump commits.
- Do NOT add any text outside the changelog block.
