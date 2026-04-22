# PR Summary Generation

Generate a GitHub pull request description for the following branch changes.

## Branch
`[[BRANCH]]`

## Commits
```
[[COMMITS]]
```

## Diff
```diff
[[DIFF]]
```

## Output Format
Generate a GitHub PR description with these sections:

### Summary
2-4 bullet points covering WHAT changed and WHY.

### Changes
Grouped bullet points by area (e.g. API, Service, Tests, Config).

### Test Plan
Checklist of what to verify before merging.

---
Rules:
- Be concise and specific — no filler phrases
- Reference file names or module names where helpful
- Do NOT include the raw diff or commit list in the output
