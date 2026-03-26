# Commit Message Generation

Generate a conventional commit message for these changes:

## Diff
```diff
[[DIFF]]
```

## Conventional Commit Format
```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Code style (formatting, no logic change)
- `refactor`: Code restructuring
- `test`: Adding or updating tests
- `chore`: Build process, dependencies

### Rules
- Subject: imperative mood, lowercase, no period, max 50 chars
- Body: use bullet points to explain WHAT and WHY (not HOW)
- Footer: reference issues, breaking changes

### Examples
```
feat(matching): add compatibility score calculation

- Implement algorithm to calculate dog compatibility based on breed, age, and preferences.
- Uses weighted scoring system.
```
```
fix(api): handle null response in dog search

- Add null check before accessing search results to prevent NullPointerException when no dogs match criteria.

Closes #73
```

## Output
Generate ONLY the commit message following the format above.
Do NOT include any explanations or markdown code blocks.
IMPORTANT: Add 'Closes #[issue-number]' only if the issue with number [issue-number] is in this repo, you can infer it from the branch name, e.g., feat/73-hande-null-response. Otherwise, omit it, e.g., main.
