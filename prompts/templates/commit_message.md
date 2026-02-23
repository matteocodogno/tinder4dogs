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
- Body: explain WHAT and WHY (not HOW), wrap at 72 chars
- Footer: reference issues, breaking changes

### Examples
```
feat(matching): add compatibility score calculation

Implement algorithm to calculate dog compatibility based on
breed, age, and preferences. Uses weighted scoring system.

Closes #123
```
```
fix(api): handle null response in dog search

Add null check before accessing search results to prevent
NullPointerException when no dogs match criteria.
```

## Output
Generate ONLY the commit message following the format above.
Do NOT include any explanations or markdown code blocks.
Add 'Closes #123' only if the issue is in this repo.
