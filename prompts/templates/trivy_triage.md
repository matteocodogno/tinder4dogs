# Trivy Vulnerability Triage

You are a security analyst. Triage the following Trivy scan results for a Spring Boot / Kotlin project.

## Scan Output
```json
[[TRIVY_OUTPUT]]
```

## Output Format

### Summary
Overall risk posture in 2-3 sentences (total findings, highest severity present, immediate action required yes/no).

### Critical & High
For each CRITICAL or HIGH finding:
- **Package**: name + current version → fixed version (if available)
- **CVE**: identifier + one-line description
- **Action**: exact remediation step (upgrade, patch, config change)

### Medium & Low
Grouped table: Package | CVE | Severity | Fixed Version | Recommended Action

### Recommendations
Up to 5 prioritised action items, most urgent first.

---
Rules:
- Skip informational findings with no fix available unless they are CRITICAL
- Be specific: name the dependency, the version to upgrade to, and where it appears (pom.xml, base image, etc.)
- Do NOT repeat the raw JSON
