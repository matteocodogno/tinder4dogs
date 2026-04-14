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
- **Examples:**
  - `feat(stripe): add webhook signature validation`
  - `fix(auth): handle 401 on token refresh`
  - `test(pdf): add export edge case coverage`
  - `chore(deps): upgrade stripe-node to v14`
- **Rule:** Never use generic messages like "fix bug" or "update code".

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
- Never close an issue without the corresponding checkbox being ticked first.
- Never tick a checkbox without closing the issue (keep them atomic).

---

# AI-DLC and Spec-Driven Development

Kiro-style Spec Driven Development implementation on AI-DLC (AI Development Life Cycle).

## Project Context
- **Steering:** `.kiro/steering/` (Guide AI with project-wide rules and context).
- **Specs:** `.kiro/specs/` (Formalize development process for individual features).

### Steering vs Specification
- **Steering:** Global project rules, architectural patterns, and product vision.
- **Specs:** Feature-specific requirements, design, and implementation tasks.

## Workflow Guidelines
- **Target Language:** All Markdown artifacts (requirements, design, tasks, research) MUST be written in the language specified in `spec.json`.
- **3-Phase Approval:** Requirements → Design → Tasks → Implementation.
- **Steering Alignment:** Always verify changes against the project steering files (`product.md`, `tech.md`, `structure.md`).

## Minimal Workflow (Gemini Skills)
- **Phase 0 (Optional):** Research steering via `read_file` or `grep_search` in `.kiro/steering/`.
- **Phase 1 (Specification):**
  - Propose/Init: Use `openspec-propose` skill or manual init in `.kiro/specs/`.
  - Requirements: Define in `requirements.md`.
  - Gap Analysis: Validate against existing codebase.
  - Design: Create `design.md` and validate.
  - Tasks: Generate `tasks.md` with acceptance criteria.
- **Phase 2 (Implementation):**
  - Implementation: Use `openspec-apply-change` to work through tasks.
  - Validation: Verify implementation against design and steering.
- **Archive:** Finalize via `openspec-archive-change`.

## Development Rules
- Human review is required at each phase (Requirements → Design → Tasks).
- Keep steering current and verify alignment during implementation.
- Act autonomously within the approved scope: gather context and complete work end-to-end.

## Steering Configuration
- Default files: `product.md`, `tech.md`, `structure.md`.
- Custom standards (API, Auth, DB, etc.) are located in `.kiro/settings/templates/steering-custom/`.
