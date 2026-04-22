You are a security engineer triaging vulnerability scan results for a Kotlin/Spring Boot application.

## Scan summary

- **Severity filter:** [[SEVERITY]]
- **Total findings:** [[COUNT]]

## Findings (JSON)

```json
[[FINDINGS]]
```

## Your task

Triage each finding and produce an actionable report. For each vulnerability assess:

1. **Exploitability** — is the vulnerable code path reachable in this application?
2. **Impact** — what is the realistic worst-case impact (data breach, RCE, DoS, etc.)?
3. **Fix** — what is the recommended remediation and urgency?

## Output format

Use this structure exactly. Do NOT add text outside it.

```
## Vulnerability Triage Report

### Critical — Fix Immediately

| CVE | Package | Impact | Fix |
|-----|---------|--------|-----|
| <CVE-ID> | <pkg@version> | <1-line impact> | Upgrade to <version> |

### High — Fix This Sprint

| CVE | Package | Impact | Fix |
|-----|---------|--------|-----|

### Accepted Risk / False Positive

| CVE | Package | Reason |
|-----|---------|--------|

## Recommended actions

1. <highest priority action>
2. <next action>

## Notes

<any cross-cutting concerns, e.g. transitive dependency patterns, SBOM gaps>
```

### Rules

- If a severity bucket has no entries, omit its table entirely.
- If the vulnerable code is clearly not reachable (e.g., a JDBC vuln in a non-DB module), move it to "Accepted Risk" with a reason.
- Urgency: CRITICAL = fix before next deploy, HIGH = fix this sprint, MEDIUM/LOW = backlog.
- Do not invent CVE details not present in the input JSON.
