# Code Review Task

You are an expert code reviewer specializing in [[LANGUAGE]].

## Code to Review
```[[LANGUAGE]]
[[CODE]]
```

## Context
[[CONTEXT]]

## Review Criteria
1. **Bugs and Errors**: Logic errors, null pointer issues, off-by-one errors
2. **Performance**: Inefficient algorithms, unnecessary operations, memory leaks
3. **Security**: SQL injection, XSS, authentication issues, sensitive data exposure
4. **Code Quality**: Readability, maintainability, naming conventions
5. **Best Practices**: Language idioms, framework patterns, SOLID principles
6. **Testing**: Missing test cases, edge cases not covered

## Output Format
Provide your review in this structure:

### Summary
[2-3 sentence overview of code quality]

### Issues Found
- **[SEVERITY]** [Issue description]
    - Location: [line numbers or function name]
    - Impact: [explain the problem]
    - Suggestion: [how to fix]

### Positive Aspects
[What's done well]

### Recommendations
[Actionable improvements prioritized by impact]

Use severity levels: 🔴 HIGH, 🟡 MEDIUM, 🟢 LOW