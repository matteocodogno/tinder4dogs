# Tinder for Dogs - Gemini CLI Guidelines

## Domain Glossary
| Term                | Definition                                    |
|---------------------|-----------------------------------------------|
| Owner               | A registered human user of the platform       |
| Dog Profile         | breed, age, photos, temperament tags          |
| Match               | A mutual "right swipe" between two dog owners |
| Compatibility Score | AI score (0-100) based on breed matrix + tags |
| Superlike           | Premium: signals strong interest before match |

## User Personas
- **Casual Owner** — finds playmates. Low tech savvy.
- **Breeder** — professional use. Needs breed/health filtering.
- **Rescue Coordinator** — manages multiple profiles. Needs bulk ops.

---

## Execution Rules
- **Strategic Planning:** Before implementing any Directive, use `enter_plan_mode` to propose a research-backed strategy and wait for explicit approval.
- **Iterative Validation:** After each sub-task in the execution phase, stop and show a `git diff` summary plus relevant test results. Wait for a "GO" before proceeding.
- **Test-Driven Fixes:** If tests fail, surface the failure immediately, propose a fix, and wait for approval before applying.
- **Task Integrity:** Strictly follow the scope defined in the approved plan or `tasks.md`.

## Commit Message Standards (Conventional Commits)
- **Format:** `<type>(<scope>): <description>`
- **Types:** `feat` | `fix` | `docs` | `style` | `refactor` | `test` | `chore` | `perf` | `ci`
- **Scope:** Task ID or module name (e.g., `feat(task-7):` or `fix(auth):`)
- **Description:** Imperative, lowercase, no period, max 72 chars.
- **Breaking Changes:** Include `BREAKING CHANGE:` in the footer.
- **Rule:** Never use generic messages like "fix bug" or "update code". Use the `git-commit` skill for intelligent generation.

## Constraints & "Never Do"
- Never implement tasks not documented in the specification or `tasks.md`.
- Never skip the quality gate for stories (especially those marked with `ambiguity_risk=High`).
- Never write acceptance criteria without at least one failure scenario.
- Never stage or commit changes unless explicitly requested.

---

## GitHub Issues Sync Rules
- When a task list is finalized:
  - Create one GitHub Issue per task.
  - Title: Task title.
  - Body: Acceptance criteria + link to relevant spec.
  - Save the issue number as a comment in the task file: `<!-- gh:#42 -->`
- When starting a task: Add the `in-progress` label.
- When finishing: Close the issue with `Closes #N` in the commit footer and tick the checkbox.

---

# Spec-Driven Development (Kiro-style)

This project follows an AI-DLC (AI Development Life Cycle) approach using Spec-Driven Development.

## Project Context
- **Steering:** `.kiro/steering/` (Project-wide rules and context).
- **Specs:** `.kiro/specs/` (Feature-specific formalization).

## Workflow Guidelines
- **Target Language:** All Markdown artifacts (requirements, design, tasks, research) must be written in the language specified in `spec.json`.
- **3-Phase Approval:** Requirements → Design → Tasks → Implementation.
- **Steering Alignment:** Always verify changes against the project steering files (`product.md`, `tech.md`, `structure.md`).

## Available Skills
Use the specialized OpenSpec skills for the development lifecycle:
- `openspec-propose`: Quick proposal of new changes.
- `openspec-explore`: Thinking partner for investigation.
- `openspec-apply-change`: Implementation of tasks.
- `openspec-archive-change`: Finalizing and archiving completed changes.
