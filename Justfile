# Justfile for Tinder for Dogs
# Includes: Infrastructure, mise, project mgmt, and AI commands

# ============================================
# Configuration
# ============================================

# LiteLLM configuration
LITELLM_URL := "http://localhost:4000/chat/completions"
LITELLM_KEY := env_var('LITELLM_MASTER_KEY')

# Project configuration
PROJECT_NAME := "tinder-for-dogs"
LANGFUSE_PROJECT := env_var_or_default('LANGFUSE_PROJECT', 'whysoserious')
LANGFUSE_URL := env_var_or_default('LANGFUSE_URL', 'http://localhost:3000')

# Colors for output
RED := '\033[0;31m'
GREEN := '\033[0;32m'
YELLOW := '\033[1;33m'
BLUE := '\033[0;34m'
NC := '\033[0m' # No Color

# ============================================
# Help & Documentation
# ============================================

# Show this help
default:
    @just --list --unsorted

# Show detailed help with examples
help:
    @echo "{{ GREEN }}Tinder for Dogs - AI4Dev Course{{ NC }}"
    @echo ""
    @echo "{{ BLUE }}Infrastructure:{{ NC }}"
    @echo "  just ai-start           - Start all AI services (LiteLLM, Langfuse, etc.)"
    @echo "  just ai-stop            - Stop all AI services"
    @echo "  just ai-status          - Check service health"
    @echo "  just ai-logs [service]  - View logs"
    @echo ""
    @echo "{{ BLUE }}Environment:{{ NC }}"
    @echo "  just check-tools        - Verify mise and all required tools"
    @echo "  just check-env          - Verify environment variables"
    @echo "  just setup              - Complete initial setup"
    @echo ""
    @echo "{{ BLUE }}Project:{{ NC }}"
    @echo "  just build              - Build the project"
    @echo "  just run                - Run Spring Boot app"
    @echo "  just test               - Run tests"
    @echo "  just clean              - Clean build artifacts"

# ============================================
# Infrastructure Management
# ============================================

# Start global AI infrastructure
ai-start:
    @ai start

# Stop global AI infrastructure
ai-stop:
    @ai stop

# Restart all or specific service
ai-restart service="":
    #!/usr/bin/env bash
    if [ -z "{{service}}" ]; then
        echo -e "{{ YELLOW }}♻️  Restarting all services...{{ NC }}"
        ai restart
    else
        echo -e "{{ YELLOW }}♻️  Restarting {{service}}...{{ NC }}"
        ai restart {{service}}
    fi

# Check AI infrastructure status
ai-status:
    #!/usr/bin/env bash
    echo -e "{{ BLUE }}📊 AI Infrastructure Status:{{ NC }}"
    echo ""
    ai status
    echo ""

# View logs for a service
ai-logs service="litellm":
    @echo "{{ BLUE }}📋 Logs for {{service}}:{{ NC }}"
    @ai logs {{service}}

# Test LiteLLM connection
ai-test:
    #!/usr/bin/env bash
    echo -e "{{ BLUE }}🧪 Testing LiteLLM connection...{{ NC }}"
    ai test

# Show AI usage costs by role
ai-costs:
    #!/usr/bin/env bash
    echo -e "{{ BLUE }}💰 AI Usage Costs by Role:{{ NC }}"
    echo ""
    ai cost

# Reset all AI infrastructure (delete data)
ai-reset:
    @ai reset

# ============================================
# Environment & Tool Management (mise)
# ============================================

# Check if all required tools are installed
check-tools:
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "{{ BLUE }}🔍 Checking required tools...{{ NC }}"
    echo ""

    MISSING=0

    check_tool() {
        if command -v $1 &> /dev/null; then
            echo -e "  {{ GREEN }}✅{{ NC }} $1"
        else
            echo -e "  {{ RED }}❌{{ NC }} $1 (not found)"
            MISSING=1
        fi
    }

    # Essential tools
    check_tool mise
    check_tool docker
    check_tool git
    check_tool just
    check_tool direnv
    check_tool gitleaks

    # Optional but recommended
    if command -v infisical &> /dev/null || command -v op &> /dev/null; then
        echo -e "  {{ GREEN }}✅{{ NC }} secret manager (infisical or 1password)"
    else
        echo -e "  {{ YELLOW }}⚠️{{ NC }}  secret manager (install infisical or 1password CLI)"
    fi

    echo ""

    if [ $MISSING -eq 1 ]; then
        echo -e "{{ RED }}❌ Some tools are missing. Please install them:{{ NC }}"
        echo ""
        echo "  brew install mise docker git just direnv gitleaks"
        echo "  brew install infisical/get-cli/infisical  # or 1password-cli"
        exit 1
    fi

    echo -e "{{ GREEN }}✅ All required tools installed{{ NC }}"
    echo ""

    # Check mise tools
    echo -e "{{ BLUE }}📦 mise-managed tools:{{ NC }}"
    mise current || echo -e "{{ YELLOW }}⚠️  Not in a mise-managed directory{{ NC }}"

## Verify mise activation
check-mise:
    #!/usr/bin/env bash
    if ! command -v mise &> /dev/null; then
        echo -e "{{ RED }}❌ mise not found. Install with: brew install mise{{ NC }}"
        exit 1
    fi

    if ! mise current &> /dev/null; then
        echo -e "{{ YELLOW }}⚠️  mise not activated in this directory{{ NC }}"
        echo "Run: cd . (to trigger mise activation)"
        exit 1
    fi

    echo -e "{{ GREEN }}✅ mise is active{{ NC }}"

## Check environment variables
check-env:
    #!/usr/bin/env bash
    echo -e "{{ BLUE }}🔐 Checking environment variables...{{ NC }}"
    echo ""

    check_var() {
        if [ -z "${!1:-}" ]; then
            echo -e "  {{ RED }}❌{{ NC }} $1 (not set)"
            return 1
        else
            echo -e "  {{ GREEN }}✅{{ NC }} $1"
            return 0
        fi
    }

    MISSING=0

    # Required variables
    check_var LITELLM_MASTER_KEY || MISSING=1
    check_var OPENAI_API_KEY || MISSING=1
    check_var ANTHROPIC_API_KEY || MISSING=1
    check_var LANGFUSE_PUBLIC_KEY || MISSING=1
    check_var LANGFUSE_SECRET_KEY || MISSING=1

    # Optional
    check_var GEMINI_API_KEY || echo -e "  {{ YELLOW }}⚠️{{ NC }}  GEMINI_API_KEY (optional)"

    echo ""

    if [ $MISSING -eq 1 ]; then
        echo "{{ RED }}❌ Some environment variables are missing{{ NC }}"
        echo ""
        echo "Make sure:"
        echo "  1. direnv is installed and configured"
        echo "  2. .envrc exists and is allowed (run: direnv allow .)"
        echo "  3. Secrets are loaded from Infisical or 1Password"
        exit 1
    fi

    echo -e "{{ GREEN }}✅ All required environment variables are set{{ NC }}"

## Install mise tools for this project
mise-install:
    @echo "{{ BLUE }}📦 Installing mise tools...{{ NC }}"
    @mise install
    @echo "{{ GREEN }}✅ Tools installed{{ NC }}"

# Show current mise versions
mise-versions:
    @echo "{{ BLUE }}📦 Current tool versions:{{ NC }}"
    @mise current

# Update mise tools
mise-update:
    @echo "{{ BLUE }}📦 Updating mise tools...{{ NC }}"
    @mise upgrade
    @echo "{{ GREEN }}✅ Tools updated{{ NC }}"

# ============================================
# Project Management
# ============================================

# Complete initial setup
setup: check-tools check-env
    #!/usr/bin/env bash
    echo -e "{{ GREEN }}🚀 Setting up Tinder for Dogs project...{{ NC }}"
    echo ""

    # Start AI infrastructure
    just ai-start

    # Install mise tools if needed
    if ! mise current &> /dev/null; then
        just mise-install
    fi

    # Create directories
    mkdir -p reviews docs/architecture docs/api

    # Build project
    echo -e "{{ BLUE }}📦 Building project...{{ NC }}"
    ./mvnw clean install

    echo ""
    echo -e "{{ GREEN }}✅ Setup complete!{{ NC }}"
    echo ""
    echo -e "{{ BLUE }}Next steps:{{ NC }}"
    echo "  1. Run the app:     just run"
    echo "  2. Run tests:       just test"
    echo "  3. View Langfuse:   just langfuse"
    echo "  4. Try AI:          just review src/main/kotlin/..."

# Build the project
build: check-mise
    @echo "{{ BLUE }}🔨 Building project...{{ NC }}"
    @./mvnw clean install
    @echo "{{ GREEN }}✅ Build complete{{ NC }}"

# Build without tests (faster)
build-fast: check-mise
    @echo "{{ BLUE }}🔨 Building project (skip tests)...{{ NC }}"
    @./mvnw clean install -DskipTests
    @echo "{{ GREEN }}✅ Build complete{{ NC }}"

# Run Spring Boot application
run: check-mise
    @echo "{{ BLUE }}🚀 Starting Tinder for Dogs...{{ NC }}"
    @./mvnw spring-boot:run

# Run in development mode (with auto-reload)
dev: check-mise
    @echo "{{ BLUE }}🔄 Starting in dev mode (auto-reload)...{{ NC }}"
    @./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true"

# Run all tests
test: check-mise
    @echo "{{ BLUE }}🧪 Running tests...{{ NC }}"
    @./mvnw test

# Run specific test class
test-class class: check-mise
    @echo -e "{{ BLUE }}🧪 Running test: {{class}}{{ NC }}"
    @./mvnw test -Dtest={{class}}

# Run tests with coverage
test-coverage: check-mise
    @echo "{{ BLUE }}🧪 Running tests with coverage...{{ NC }}"
    @./mvnw verify
    @echo "{{ GREEN }}✅ Coverage report: target/site/jacoco/index.html{{ NC }}"

# Clean build artifacts
clean:
    @echo "{{ YELLOW }}🧹 Cleaning build artifacts...{{ NC }}"
    @./mvnw clean
    @rm -rf target/
    @echo "{{ GREEN }}✅ Clean complete{{ NC }}"

# Package JAR file
package: check-mise
    @echo "{{ BLUE }}📦 Packaging application...{{ NC }}"
    @./mvnw clean package -DskipTests
    @echo "{{ GREEN }}✅ JAR created: target/{{PROJECT_NAME}}.jar{{ NC }}"


# ============================================
# Helper: Call AI with role
# ============================================

# Internal: Call AI with specific role
_call_ai role prompt_file *metadata:
    #!/usr/bin/env bash
    set -euo pipefail

    # Build metadata JSON from key=value pairs
    META='{"role": "{{role}}", "user": "'"$USER"'", "project": "{{PROJECT_NAME}}"'

    # Add custom metadata
    for item in {{metadata}}; do
        KEY=$(echo "$item" | cut -d= -f1)
        VALUE=$(echo "$item" | cut -d= -f2-)
        META="$META, \"$KEY\": \"$VALUE\""
    done
    META="$META}"

    # Read prompt from file and escape for JSON
    PROMPT_CONTENT=$(cat "{{prompt_file}}")

    # Build complete JSON payload using jq
    JSON_PAYLOAD=$(jq -n \
      --arg model "{{role}}" \
      --arg content "$PROMPT_CONTENT" \
      --argjson metadata "$META" \
      '{
        model: $model,
        messages: [{role: "user", content: $content}],
        metadata: $metadata
      }')

    # Call LiteLLM
    RESPONSE=$(curl -s -X POST {{LITELLM_URL}} \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer {{LITELLM_KEY}}" \
      -d "$JSON_PAYLOAD")

    # Check for errors
    if echo "$RESPONSE" | jq -e '.error' > /dev/null 2>&1; then
        echo -e "{{ RED }}❌ API Error:{{ NC }}" >&2
        echo "$RESPONSE" | jq '.error' >&2
        exit 1
    fi

    # Extract and return content
    echo "$RESPONSE" | jq -r '.choices[0].message.content'

# ============================================
# ROLE: COMMITTER (Commit Messages)
# ============================================

# Generate conventional commit message
commit-msg:
    #!/usr/bin/env bash
    DIFF=$(git diff --staged)

    if [ -z "$DIFF" ]; then
      echo "❌ No staged changes"
      exit 1
    fi

    TEMPLATE=$(cat prompts/templates/commit_message.md)

    DIFF_FILE=$(mktemp)
    PROMPT_FILE=$(mktemp)
    trap 'rm -f "$DIFF_FILE" "$PROMPT_FILE"' EXIT

    # Create prompt by replacing placeholders
    PROMPT="${TEMPLATE//\[\[DIFF\]\]/$DIFF}"

    # Write prompt to temp file
    echo "$PROMPT" > "$PROMPT_FILE"

    just _call_ai committer "$PROMPT_FILE" task=commit_message

# Commit with AI-generated message
commit:
    #!/usr/bin/env bash
    echo "💬 Generating commit message..."

    MSG=$(just commit-msg) || MSG=""

    if [ -z "$MSG" ]; then
      echo "❌ Failed to generate commit message"
      exit 1
    fi

    echo "📝 Commit message:"
    echo "$MSG"
    echo ""
    echo "Proceed? (y/N)"
    read -r confirm

    if [ "$confirm" = "y" ]; then
      git commit -m "$MSG"
      echo "✅ Committed"
    else
      echo "❌ Aborted"
    fi

# ============================================
# ROLE: COMMITTER (Changelog)
# ============================================

# Generate a Keep-a-Changelog between two refs (default: last tag → HEAD)
changelog from="" to="HEAD":
    #!/usr/bin/env bash
    set -euo pipefail

    FROM="{{from}}"
    TO="{{to}}"

    # Default FROM to the latest annotated/lightweight tag
    if [ -z "$FROM" ]; then
        FROM=$(git describe --tags --abbrev=0 2>/dev/null || true)
        if [ -z "$FROM" ]; then
            echo -e "{{ RED }}❌ No tags found. Provide an explicit FROM ref:{{ NC }}"
            echo -e "   just changelog <from> [to]"
            exit 1
        fi
    fi

    echo -e "{{ BLUE }}📋 Collecting commits $FROM..$TO...{{ NC }}"

    GIT_LOG=$(git log "$FROM..$TO" --pretty=format:"%s%n%b" --no-merges)

    if [ -z "$GIT_LOG" ]; then
        echo -e "{{ YELLOW }}⚠️  No commits found between $FROM and $TO{{ NC }}"
        exit 0
    fi

    TEMPLATE=$(cat prompts/templates/changelog.md)

    # Replace template placeholders
    PROMPT="${TEMPLATE//\[\[GIT_LOG\]\]/$GIT_LOG}"
    PROMPT="${PROMPT//\[\[FROM\]\]/$FROM}"
    PROMPT="${PROMPT//\[\[TO\]\]/$TO}"

    TEMP_PROMPT=$(mktemp)
    trap 'rm -f "$TEMP_PROMPT"' EXIT
    echo "$PROMPT" > "$TEMP_PROMPT"

    mkdir -p changelogs
    CHANGELOG_FILE="changelogs/CHANGELOG-${TO//\//-}-$(date +%Y%m%d-%H%M%S).md"

    just _call_ai committer "$TEMP_PROMPT" task=changelog from="$FROM" to="$TO" | tee "$CHANGELOG_FILE"

    echo ""
    echo -e "{{ GREEN }}✅ Changelog saved to: $CHANGELOG_FILE{{ NC }}"

# ============================================
# ROLE: REVIEWER (Code Review)
# ============================================

# Review code file (saves report)
review file:
    #!/usr/bin/env bash
    echo -e "{{ BLUE }}🔍 LLM (Reviewer) is analyzing {{file}}...{{ NC }}"

    if [ ! -f "{{file}}" ]; then
        echo -e "{{ RED }}❌ File not found: {{file}}{{ NC }}"
        exit 1
    fi

    CODE=$(cat "{{file}}")

    TEMPLATE=$(cat prompts/templates/code_review.md)

    # Create prompt by replacing placeholders
    PROMPT="${TEMPLATE//\[\[LANGUAGE\]\]/kotlin}"
    PROMPT="${PROMPT//\[\[CONTEXT\]\]/Spring Boot service}"
    PROMPT="${PROMPT//\[\[CODE\]\]/$CODE}"

    # Write prompt to temp file
    TEMP_PROMPT=$(mktemp)
    echo "$PROMPT" > "$TEMP_PROMPT"

    mkdir -p reviews
    REVIEW_FILE="reviews/$(basename "{{file}}" .kt)-review-$(date +%Y%m%d-%H%M%S).md"

    # Pass temp file path instead of content
    just _call_ai reviewer "$TEMP_PROMPT" task=code_review file="{{file}}" | tee "$REVIEW_FILE"

    # Cleanup
    rm -f "$TEMP_PROMPT"

    echo ""
    echo -e "{{ GREEN }}✅ Review saved to: $REVIEW_FILE{{ NC }}"

# ============================================
# ROLE: TESTER (Test Generation)
# ============================================

# Generate tests for a file
gen-tests file:
    #!/usr/bin/env bash
    echo -e "{{ BLUE }}🧪 LLM (Tester) is generating tests...{{ NC }}"

    if [ ! -f "{{file}}" ]; then
        echo -e "{{ RED }}❌ File not found: {{file}}{{ NC }}"
        exit 1
    fi

    CODE=$(cat {{file}})
    TEMPLATE=$(cat prompts/templates/generate_tests.md)

    # Create prompt by replacing placeholders
    PROMPT="${TEMPLATE//\[\[CODE\]\]/$CODE}"
    PROMPT="${PROMPT//\[\[FRAMEWORK\]\]/JUnit 5, MockK, Spring Boot Test}"

    # Write prompt to temp file
    TEMP_PROMPT=$(mktemp)
    echo "$PROMPT" > "$TEMP_PROMPT"

    # Determine test file path
    TEST_FILE=$(echo {{file}} | \
      sed 's|src/main/kotlin|src/test/kotlin|g' | \
      sed 's|\.kt|Test.kt|g')

    mkdir -p $(dirname "$TEST_FILE")
    just _call_ai tester "$TEMP_PROMPT" task=code_review file="{{file}}" | tee "$TEST_FILE"

    # Cleanup
    rm -f "$TEMP_PROMPT"

    echo -e "{{ GREEN }}✅ Tests created at: $TEST_FILE{{ NC }}"
    echo -e "{{ BLUE }}💡 Run tests: just test{{ NC }}"

# Comparison: cloud vs local
compare-models prompt="Explain what a token is in 2 sentences":
    #!/usr/bin/env bash
    echo "🔵 Cloud (writer — Claude Sonnet):"
    curl -s -X POST {{LITELLM_URL}} \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer {{LITELLM_KEY}}" \
      -d '{"model":"writer","messages":[{"role":"user","content":"{{prompt}}"}]}' \
      | jq -r '.choices[0].message.content'
    echo ""
    echo "🟢 Local (local-fast — Llama 3.2 3B):"
    curl -s -X POST {{LITELLM_URL}} \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer {{LITELLM_KEY}}" \
      -d '{"model":"local-fast","messages":[{"role":"user","content":"{{prompt}}"}]}' \
      | jq -r '.choices[0].message.content'

# ============================================
# CI Pipeline
# ============================================

# Run lint
lint: check-mise
    @echo "{{ BLUE }}🔍 Running ktlint...{{ NC }}"
    @./mvnw ktlint:check
    @echo "{{ GREEN }}✅ Lint passed{{ NC }}"

# Run lint, tests, and build in sequence
ci: check-mise
    #!/usr/bin/env bash
    set -euo pipefail

    echo -e "{{ BLUE }}[1/3] 🔍 Linting...{{ NC }}"
    ./mvnw ktlint:check
    echo -e "{{ GREEN }}    ✅ Lint passed{{ NC }}"
    echo ""

    echo -e "{{ BLUE }}[2/3] 🧪 Running tests...{{ NC }}"
    ./mvnw test
    echo -e "{{ GREEN }}    ✅ Tests passed{{ NC }}"
    echo ""

    echo -e "{{ BLUE }}[3/3] 🔨 Building (skip tests)...{{ NC }}"
    ./mvnw package -DskipTests
    echo -e "{{ GREEN }}    ✅ Build complete{{ NC }}"
    echo ""

    echo -e "{{ GREEN }}🚀 CI pipeline passed{{ NC }}"

# ============================================
# ROLE: REVIEWER (PR Summary)
# ============================================

# Generate an AI PR summary from current branch diff (default base: main)
pr-summary base="main":
    #!/usr/bin/env bash
    set -euo pipefail

    BRANCH=$(git branch --show-current)
    MERGE_BASE=$(git merge-base "{{base}}" HEAD)
    DIFF=$(git diff "$MERGE_BASE" HEAD)
    COMMITS=$(git log "$MERGE_BASE..HEAD" --pretty=format:"%h %s" --no-merges)

    if [ -z "$DIFF" ]; then
        echo -e "{{ YELLOW }}⚠️  No diff between {{base}} and $BRANCH{{ NC }}"
        exit 0
    fi

    TEMPLATE=$(cat prompts/templates/pr_summary.md)
    PROMPT="${TEMPLATE//\[\[BRANCH\]\]/$BRANCH}"
    PROMPT="${PROMPT//\[\[BASE\]\]/{{base}}}"
    PROMPT="${PROMPT//\[\[COMMITS\]\]/$COMMITS}"
    PROMPT="${PROMPT//\[\[DIFF\]\]/$DIFF}"

    TEMP_PROMPT=$(mktemp)
    trap 'rm -f "$TEMP_PROMPT"' EXIT
    echo "$PROMPT" > "$TEMP_PROMPT"

    mkdir -p reviews
    OUT="reviews/pr-summary-${BRANCH//\//-}-$(date +%Y%m%d-%H%M%S).md"

    echo -e "{{ BLUE }}📝 Generating PR summary for $BRANCH → {{base}}...{{ NC }}"
    just _call_ai reviewer "$TEMP_PROMPT" task=pr_summary branch="$BRANCH" base="{{base}}" | tee "$OUT"

    echo ""
    echo -e "{{ GREEN }}✅ PR summary saved to: $OUT{{ NC }}"

# ============================================
# Security Scanning
# ============================================

# Run Gitleaks secret detection (fails on findings)
scan-secrets:
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "{{ BLUE }}🔐 Running Gitleaks secret detection...{{ NC }}"

    if gitleaks detect --source . --no-banner 2>&1; then
        echo -e "{{ GREEN }}✅ No secrets detected{{ NC }}"
    else
        echo -e "{{ RED }}❌ Secrets detected — review findings above{{ NC }}"
        exit 1
    fi

# Run Trivy vulnerability scan with AI triage (default: CRITICAL,HIGH)
scan-vuln severity="CRITICAL,HIGH":
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "{{ BLUE }}🛡️  Running Trivy scan ({{severity}})...{{ NC }}"

    REPORT=$(mktemp --suffix=.json)
    trap 'rm -f "$REPORT"' EXIT

    trivy fs . --format json --severity "{{severity}}" --quiet -o "$REPORT"

    VULN_COUNT=$(jq '[.Results[]? | .Vulnerabilities? // [] | .[]] | length' "$REPORT")

    if [ "$VULN_COUNT" -eq 0 ]; then
        echo -e "{{ GREEN }}✅ No {{severity}} vulnerabilities found{{ NC }}"
        exit 0
    fi

    echo -e "{{ YELLOW }}⚠️  Found $VULN_COUNT finding(s). Running AI triage...{{ NC }}"
    echo ""

    # Cap at 20 findings to keep prompt size manageable
    FINDINGS=$(jq '[.Results[]? | .Vulnerabilities? // [] | .[] |
      {id: .VulnerabilityID, pkg: .PkgName, severity: .Severity,
       title: .Title, description: (.Description // "" | .[0:200])}
    ] | .[:20]' "$REPORT")

    TEMPLATE=$(cat prompts/templates/trivy_triage.md)
    PROMPT="${TEMPLATE//\[\[SEVERITY\]\]/{{severity}}}"
    PROMPT="${PROMPT//\[\[COUNT\]\]/$VULN_COUNT}"
    PROMPT="${PROMPT//\[\[FINDINGS\]\]/$FINDINGS}"

    TEMP_PROMPT=$(mktemp)
    trap 'rm -f "$TEMP_PROMPT"' EXIT
    echo "$PROMPT" > "$TEMP_PROMPT"

    mkdir -p reviews
    OUT="reviews/trivy-triage-$(date +%Y%m%d-%H%M%S).md"

    just _call_ai reviewer "$TEMP_PROMPT" task=trivy_triage severity="{{severity}}" | tee "$OUT"

    echo ""
    echo -e "{{ GREEN }}✅ Triage report saved to: $OUT{{ NC }}"

# ============================================
# ROLE: COMMITTER (Changelog — no tag required)
# ============================================

# Generate changelog for the last N commits on the current branch (no tags needed)
changelog-recent n="20":
    #!/usr/bin/env bash
    set -euo pipefail

    GIT_LOG=$(git log -"{{n}}" --pretty=format:"%s%n%b" --no-merges)

    if [ -z "$GIT_LOG" ]; then
        echo -e "{{ YELLOW }}⚠️  No commits found{{ NC }}"
        exit 0
    fi

    FROM="HEAD~{{n}}"
    TO="HEAD ($(git rev-parse --short HEAD))"

    echo -e "{{ BLUE }}📋 Generating changelog for last {{n}} commits...{{ NC }}"

    TEMPLATE=$(cat prompts/templates/changelog.md)
    PROMPT="${TEMPLATE//\[\[GIT_LOG\]\]/$GIT_LOG}"
    PROMPT="${PROMPT//\[\[FROM\]\]/$FROM}"
    PROMPT="${PROMPT//\[\[TO\]\]/$TO}"

    TEMP_PROMPT=$(mktemp)
    trap 'rm -f "$TEMP_PROMPT"' EXIT
    echo "$PROMPT" > "$TEMP_PROMPT"

    mkdir -p changelogs
    CHANGELOG_FILE="changelogs/CHANGELOG-recent-$(date +%Y%m%d-%H%M%S).md"

    just _call_ai committer "$TEMP_PROMPT" task=changelog | tee "$CHANGELOG_FILE"

    echo ""
    echo -e "{{ GREEN }}✅ Changelog saved to: $CHANGELOG_FILE{{ NC }}"

# ============================================
# Langfuse Cost Reporting
# ============================================

# Report AI cost per run from Langfuse (default: last 1 day)
cost-report days="1":
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "{{ BLUE }}💰 Fetching AI cost from Langfuse (last {{days}} day(s))...{{ NC }}"
    echo ""

    # ISO-8601 timestamp for N days ago (GNU date / BSD date compatible)
    SINCE=$(date -d "-{{days}} days" -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null \
         || date -v-{{days}}d -u +%Y-%m-%dT%H:%M:%SZ)

    AUTH=$(printf "%s:%s" "$LANGFUSE_PUBLIC_KEY" "$LANGFUSE_SECRET_KEY" | base64 -w 0 2>/dev/null || \
           printf "%s:%s" "$LANGFUSE_PUBLIC_KEY" "$LANGFUSE_SECRET_KEY" | base64)

    RESPONSE=$(curl -s \
      -H "Authorization: Basic $AUTH" \
      "{{LANGFUSE_URL}}/api/public/observations?type=GENERATION&fromStartTime=${SINCE}&limit=100&page=1")

    # Check for API error
    if echo "$RESPONSE" | jq -e '.error // .message' > /dev/null 2>&1; then
        echo -e "{{ RED }}❌ Langfuse API error:{{ NC }}"
        echo "$RESPONSE" | jq '.error // .message'
        exit 1
    fi

    TOTAL=$(echo "$RESPONSE" | jq '.meta.totalItems // (.data | length)')
    echo -e "{{ BLUE }}Total generations: $TOTAL{{ NC }}"
    echo ""

    # Per-task breakdown
    echo -e "{{ BLUE }}── Cost by task ────────────────────────────────{{ NC }}"
    echo "$RESPONSE" | jq -r '
      [ .data[] |
        {
          task:  (.metadata.task  // "—"),
          model: (.model          // "unknown"),
          cost:  (.calculatedTotalCost // 0),
          tokens: ((.usage.totalTokens // .usage.total // 0) | tonumber)
        }
      ] |
      group_by(.task) |
      map({
        task:   .[0].task,
        runs:   length,
        cost:   ([.[].cost]   | add),
        tokens: ([.[].tokens] | add)
      }) |
      sort_by(-.cost) |
      ["Task", "Runs", "USD", "Tokens"],
      ["----", "----", "---", "------"],
      (.[] | [.task, (.runs|tostring),
              ("$" + (.cost * 10000 | round / 10000 | tostring)),
              (.tokens|tostring)])
      | @tsv
    ' | column -t

    echo ""

    # Per-model breakdown
    echo -e "{{ BLUE }}── Cost by model ───────────────────────────────{{ NC }}"
    echo "$RESPONSE" | jq -r '
      [ .data[] |
        {
          model: (.model // "unknown"),
          cost:  (.calculatedTotalCost // 0),
          tokens: ((.usage.totalTokens // .usage.total // 0) | tonumber)
        }
      ] |
      group_by(.model) |
      map({
        model:  .[0].model,
        runs:   length,
        cost:   ([.[].cost]   | add),
        tokens: ([.[].tokens] | add)
      }) |
      sort_by(-.cost) |
      ["Model", "Runs", "USD", "Tokens"],
      ["-----", "----", "---", "------"],
      (.[] | [.model, (.runs|tostring),
              ("$" + (.cost * 10000 | round / 10000 | tostring)),
              (.tokens|tostring)])
      | @tsv
    ' | column -t

    echo ""

    # Grand total
    GRAND=$(echo "$RESPONSE" | jq '[.data[].calculatedTotalCost // 0] | add')
    echo -e "{{ GREEN }}Grand total (last {{days}} day(s)): \$${GRAND}{{ NC }}"