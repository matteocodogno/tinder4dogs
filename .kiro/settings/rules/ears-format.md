# EARS Format Guidelines

## Overview
EARS (Easy Approach to Requirements Syntax) is the standard format for acceptance criteria in spec-driven development.

EARS patterns describe the logical structure of a requirement (condition + subject + response) and are not tied to any particular natural language.  
All acceptance criteria should be written in the target language configured for the specification (for example, `spec.json.language` / `en`).  
Keep EARS trigger keywords and fixed phrases in English (`When`, `If`, `While`, `Where`, `The system shall`, `The [system] shall`) and localize only the variable parts (`[event]`, `[precondition]`, `[trigger]`, `[feature is included]`, `[response/action]`) into the target language. Do not interleave target-language text inside the trigger or fixed English phrases themselves.

## Primary EARS Patterns

### 1. Event-Driven Requirements
- **Pattern**: When [event], the [system] shall [response/action]
- **Use Case**: Responses to specific events or triggers
- **Example**: When user clicks checkout button, the Checkout Service shall validate cart contents

### 2. State-Driven Requirements
- **Pattern**: While [precondition], the [system] shall [response/action]
- **Use Case**: Behavior dependent on system state or preconditions
- **Example**: While payment is processing, the Checkout Service shall display loading indicator

### 3. Unwanted Behavior Requirements
- **Pattern**: If [trigger], the [system] shall [response/action]
- **Use Case**: System response to errors, failures, or undesired situations
- **Example**: If invalid credit card number is entered, then the website shall display error message

### 4. Optional Feature Requirements
- **Pattern**: Where [feature is included], the [system] shall [response/action]
- **Use Case**: Requirements for optional or conditional features
- **Example**: Where the car has a sunroof, the car shall have a sunroof control panel

### 5. Ubiquitous Requirements
- **Pattern**: The [system] shall [response/action]
- **Use Case**: Always-active requirements and fundamental system properties
- **Example**: The mobile phone shall have a mass of less than 100 grams

## Combined Patterns
- While [precondition], when [event], the [system] shall [response/action]
- When [event] and [additional condition], the [system] shall [response/action]

## Subject Selection Guidelines
- **Software Projects**: Use concrete system/service name (e.g., "Checkout Service", "User Auth Module")
- **Process/Workflow**: Use responsible team/role (e.g., "Support Team", "Review Process")
- **Non-Software**: Use appropriate subject (e.g., "Marketing Campaign", "Documentation")

## Quality Criteria
- Requirements must be testable, verifiable, and describe a single behavior.
- Use objective language: "shall" for mandatory behavior, "should" for recommendations; avoid ambiguous terms.
- Follow EARS syntax: [condition], the [system] shall [response/action].

---

## Requirements Interview Rules

When generating requirements, first conduct a discovery interview.

Rules you must follow without exception:
- Ask ONE question at a time. Wait for the answer before continuing.
- NEVER provide answers, solutions, architecture recommendations,
  or implementation advice.
- Explore "what" and "why" before "how". Never jump to implementation.
- Never write requirements.md until the interview is complete
  and the user has confirmed.
- Be conversational and encouraging. Make the user feel heard.
- If the user asks for advice respond:
  "I'm here to help you think through your product by asking questions.
  Let me ask you…" then continue with a relevant question.

Systematically cover all of these areas. Track what has been explored
and always move toward uncovered areas:

1. Problem Space — What problem is being solved? How severe?
   How do people handle it today?
2. Users & Stakeholders — Who will use this? Characteristics, needs,
   pain points? Multiple user types?
3. Core Value Proposition — What unique value does this provide?
   What makes it different from alternatives?
4. Key Features & Functionality — What are the essential capabilities?
   What must users be able to do?
5. User Workflows — How will users interact day-to-day?
   What are the main journeys?
6. Success Metrics — How will success be measured?
   What outcomes are expected?
7. Constraints & Requirements — Technical, business, or regulatory
   constraints? Must-haves vs nice-to-haves?
8. Scope & Priorities — What's in scope for v1?
   What comes in later iterations?

When all 8 areas are covered, say:
"I think we have a solid foundation. Here's a summary of what
we've explored: [summary]. Running analysis before writing
requirements.md."

---

## Pre-write Analysis (run after interview, before writing)

### Ambiguity Report
Flag every statement that is vague, contradictory, or open to
multiple interpretations. For each:
- Quote the original statement
- Explain why it is ambiguous
- Propose a clarified rewrite
- Mark as BLOCKING (must resolve before design) or NON-BLOCKING

Present BLOCKING items to the user and wait for resolution.
Only proceed once all BLOCKING items are resolved.

### NFR Extraction
Extract all non-functional requirements — explicit or implied.
Embed them in requirements.md under ## Non-Functional Requirements:
- Performance   (latency, throughput, SLA targets)
- Security      (auth, encryption, compliance, data residency)
- Scalability   (load, concurrency, storage growth)
- Reliability   (uptime, RTO, RPO, failover)
- Observability (logging, tracing, alerting)
- Usability     (accessibility, browser/device support)
- Constraints   (tech stack mandates, budget, team skills)
For each NFR: source area, acceptance threshold, priority (P0/P1/P2).

### Open Questions
List every decision that cannot be made from current answers.
Embed in requirements.md under ## Open Questions:
- Q: [question]
  Impact: [what gets blocked until answered]
  Owner: [product / tech / legal]
