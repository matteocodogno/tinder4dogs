# Light Discovery Process for Extensions

## Objective
Quickly analyze existing system and integration requirements for feature extensions.

## Focused Discovery Steps

### 1. Extension Point Analysis
**Identify Integration Approach**:
- Locate existing extension points or interfaces
- Determine modification scope (files, components)
- Check for existing patterns to follow
- Identify backward compatibility requirements

### 2. Dependency Check
**Verify Compatibility**:
- Check version compatibility of new dependencies
- Validate API contracts haven't changed
- Ensure no breaking changes in pipeline

### 3. Quick Technology Verification
**For New Libraries Only**:
- Use WebSearch for official documentation
- Verify basic usage patterns
- Check for known compatibility issues
- Confirm licensing compatibility
- Record key findings in `research.md` (technology alignment section)

### 4. Integration Risk Assessment
**Quick Risk Check**:
- Impact on existing functionality
- Performance implications
- Security considerations
- Testing requirements

## When to Escalate to Full Discovery
Switch to full discovery if you find:
- Significant architectural changes needed
- Complex external service integrations
- Security-sensitive implementations
- Performance-critical components
- Unknown or poorly documented dependencies

## Output Requirements
- Clear integration approach (note boundary impacts in `research.md`)
- List of files/components to modify
- New dependencies with versions
- Integration risks and mitigations
- Testing focus areas
---

## Sequence Diagram (Light)
Always generate docs/diagrams/[feature]-sequence.md.
Follow the Sequence Diagram scaffold in templates/design.md.
Include integration points with the existing system.

## Corner Cases (Light — focused)
Write a ## Corner Cases section in design.md covering only:

### Integration failure modes
For every new external dependency or integration point:
- What happens if it is slow / errors / unavailable?
- What is the fallback or degraded behaviour?

### Security edge cases
- Any new auth bypass or privilege escalation vectors?
- Token or session expiry during the extended operation?

Omit input boundary, state/timing, and data edge cases unless
full discovery has been triggered.

## Research Log (Light)
Append to docs/research/[feature]-research.md:
- New dependencies added with versions and rationale
- Integration risks and mitigations
- Any compatibility issues found
- References for technology choices
