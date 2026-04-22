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

# Run linting with ktlint (fix=true to auto-format)
lint fix="":
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "\033[0;34m🔍 Running ktlint...\033[0m"
    if [ -n "{{fix}}" ]; then
        echo -e "\033[1;33m✏️  Auto-fixing code style...\033[0m"
        ./mvnw ktlint:format
    else
        ./mvnw ktlint:check
    fi
    echo -e "\033[0;32m✅ Linting complete\033[0m"

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

# Complete CI pipeline: lint → test → build
jest: check-mise
    @echo "{{ BLUE }}🚀 Running CI pipeline: lint → test → build...{{ NC }}"
    @just lint
    @echo ""
    @just test
    @echo ""
    @just build
    @echo ""
    @echo "{{ GREEN }}✅ CI pipeline complete{{ NC }}"

# Full CI checks: build → lint → security → costs (fail-fast)
ci: check-mise
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "{{ BLUE }}🚀 Running full CI pipeline...{{ NC }}"
    echo ""

    # 1. Clean build
    echo -e "{{ BLUE }}📦 Step 1: Clean build{{ NC }}"
    just build || exit 1
    echo ""

    # 2. Lint with auto-fix
    echo -e "{{ BLUE }}🔍 Step 2: Lint (auto-fix){{ NC }}"
    just lint fix=true
    echo ""

    # 3. Security (gitleaks)
    echo -e "{{ BLUE }}🔐 Step 3: Secret scan (gitleaks){{ NC }}"
    just gitleaks || exit 1
    echo ""

    # 4. Vulnerability scan (trivy)
    echo -e "{{ BLUE }}🔒 Step 4: Vulnerability scan (trivy){{ NC }}"
    just trivy || exit 1
    echo ""

    # 5. AI costs
    echo -e "{{ BLUE }}💰 Step 5: Report AI costs{{ NC }}"
    COST=$(just ai-cost 2>/dev/null || echo "0.0000")
    echo "Total cost: $COST"
    echo ""

    echo -e "{{ GREEN }}✅ CI pipeline passed{{ NC }}"

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

# ============================================
# ROLE: WRITER (PR Summary Generation)
# ============================================

# Generate AI-powered PR summary from current branch diff
pr-summary:
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "\033[0;34m📝 Generating PR summary from current branch...\033[0m"
    echo ""
    DIFF=$(git diff origin/main..HEAD 2>/dev/null || git diff HEAD~1..HEAD 2>/dev/null || echo "")
    if [ -z "$DIFF" ]; then
        echo -e "\033[1;33m⚠️  No changes found\033[0m"
        exit 0
    fi
    TEMPLATE=$(cat prompts/templates/pr_summary.md)
    PROMPT="${TEMPLATE//\[\[DIFF\]\]/$DIFF}"
    PROMPT_FILE=$(mktemp)
    trap 'rm -f "$PROMPT_FILE"' EXIT
    echo "$PROMPT" > "$PROMPT_FILE"
    echo -e "\033[0;34m🤖 Calling AI (writer role) to generate summary...\033[0m"
    echo ""
    just _call_ai writer "$PROMPT_FILE" task=pr_summary

# ============================================
# SECURITY: Gitleaks Secret Detection
# ============================================

# Run Gitleaks on PR commits with AI triage
gitleaks:
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "\033[0;34m🔐 Running Gitleaks on PR commits...\033[0m"
    SECRETS=$(gitleaks detect --source . --verbose --report-format json 2>&1 || echo "{}")
    SECRET_COUNT=$(echo "$SECRETS" | jq '.[] | length' 2>/dev/null || echo "0")
    if [ "$SECRET_COUNT" = "0" ] || [ -z "$(echo "$SECRETS" | jq -r '.[] // empty' 2>/dev/null)" ]; then
        echo -e "\033[0;32m✅ No secrets detected\033[0m"
        exit 0
    fi
    echo -e "\033[1;31m🚨 SECRETS FOUND - Running AI triage...\033[0m"
    echo ""
    TEMPLATE=$(cat prompts/templates/gitleaks_triage.md)
    PROMPT="${TEMPLATE//\[\[SECRETS\]\]/$SECRETS}"
    PROMPT_FILE=$(mktemp)
    trap 'rm -f "$PROMPT_FILE"' EXIT
    echo "$PROMPT" > "$PROMPT_FILE"
    echo -e "\033[0;34m🤖 Calling AI (triage role) for security analysis...\033[0m"
    echo ""
    just _call_ai triage "$PROMPT_FILE" task=secret_triage
    echo ""
    echo -e "\033[0;31m❌ FAILING BUILD - Secrets must be remediated\033[0m"
    exit 1

# ============================================
# ROLE: TRIAGE (Trivy Vulnerability Scanning)
# ============================================

# Run Trivy vulnerability scanning with AI triage of critical findings
trivy:
    #!/usr/bin/env bash
    set -euo pipefail
    echo -e "\033[0;34m🔒 Running Trivy vulnerability scan...\033[0m"
    SCAN_OUTPUT=$(trivy fs . --severity CRITICAL --format json 2>/dev/null || echo "{}")
    CRITICAL_COUNT=$(echo "$SCAN_OUTPUT" | jq '[.Results[]?.Misconfigurations[]? + .Results[]?.Vulnerabilities[]?] | length' 2>/dev/null || echo "0")
    if [ "$CRITICAL_COUNT" = "0" ]; then
        echo -e "\033[0;32m✅ No CRITICAL vulnerabilities found\033[0m"
        exit 0
    fi
    echo -e "\033[1;33m⚠️  Found $CRITICAL_COUNT CRITICAL vulnerabilities\033[0m"
    echo ""
    TEMPLATE=$(cat prompts/templates/trivy_triage.md)
    PROMPT="${TEMPLATE//\[\[VULNERABILITIES\]\]/$SCAN_OUTPUT}"
    PROMPT_FILE=$(mktemp)
    trap 'rm -f "$PROMPT_FILE"' EXIT
    echo "$PROMPT" > "$PROMPT_FILE"
    echo -e "\033[0;34m🤖 Calling AI (triage role) for vulnerability analysis...\033[0m"
    echo ""
    just _call_ai triage "$PROMPT_FILE" task=vulnerability_triage

# ============================================
# ROLE: CI-SUMMARIZER (Changelog Generation)
# ============================================

# Generate changelog from git commits
changelog from="" to="":
    #!/usr/bin/env bash
    set -euo pipefail
    FROM="{{from}}"
    TO="{{to}}"
    [ -z "$FROM" ] && FROM=$(git describe --tags --abbrev=0 2>/dev/null || echo "HEAD~10")
    [ -z "$TO" ] && TO="HEAD"
    echo -e "\033[0;34m📝 Generating changelog from $FROM to $TO...\033[0m"
    COMMITS=$(git log "$FROM..$TO" --pretty=format:"%s%n%b" --no-merges 2>/dev/null || echo "")
    if [ -z "$COMMITS" ]; then
        echo -e "\033[1;33m⚠️  No commits found\033[0m"
        exit 0
    fi
    TEMPLATE=$(cat prompts/templates/changelog.md)
    PROMPT="${TEMPLATE//\[\[COMMITS\]\]/$COMMITS}"
    PROMPT_FILE=$(mktemp)
    trap 'rm -f "$PROMPT_FILE"' EXIT
    echo "$PROMPT" > "$PROMPT_FILE"
    CHANGELOG=$(just _call_ai ci-summarizer "$PROMPT_FILE" from="$FROM" to="$TO")
    echo "$CHANGELOG"
    if [ -f "CHANGELOG.md" ]; then
        echo -e "\033[0;34m📄 Updating CHANGELOG.md...\033[0m"
        { echo "# Changelog"; echo ""; echo "## [$TO] - $(date +%Y-%m-%d)"; echo ""; echo "$CHANGELOG"; echo ""; tail -n +2 CHANGELOG.md; } > CHANGELOG.md.tmp
        mv CHANGELOG.md.tmp CHANGELOG.md
        echo -e "\033[0;32m✅ CHANGELOG.md updated\033[0m"
    else
        echo -e "\033[0;34m📄 Creating CHANGELOG.md...\033[0m"
        { echo "# Changelog"; echo ""; echo "## [$TO] - $(date +%Y-%m-%d)"; echo ""; echo "$CHANGELOG"; } > CHANGELOG.md
        echo -e "\033[0;32m✅ CHANGELOG.md created\033[0m"
    fi

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
# ROLE: CHANGELOG (Release Notes)
# ============================================
# Internal: Generate changelog content only (no status messages)
_changelog-generate from_tag to_tag:
    #!/usr/bin/env bash
    set -euo pipefail

    FROM_TAG="{{from_tag}}"
    TO_TAG="{{to_tag}}"

    # Verify refs exist
    if ! git rev-parse --verify "$FROM_TAG" >/dev/null 2>&1; then
        echo -e "{{ RED }}❌ Invalid ref: $FROM_TAG{{ NC }}" >&2
        exit 1
    fi

    if ! git rev-parse --verify "$TO_TAG" >/dev/null 2>&1; then
        echo -e "{{ RED }}❌ Invalid ref: $TO_TAG{{ NC }}" >&2
        exit 1
    fi

    # Get commits between refs (works for tags, branches, commits)
    COMMITS=$(git log "${FROM_TAG}..${TO_TAG}" --pretty=format:"%s%n%b" --no-merges -- 2>&1)

    if [ -z "$COMMITS" ]; then
        echo -e "{{ YELLOW }}⚠️  No commits found between $FROM_TAG and $TO_TAG{{ NC }}" >&2
        exit 0
    fi

    # Build JSON payload using jq
    JSON_PAYLOAD=$(jq -n \
      --arg commits "$COMMITS" \
      --arg from "$FROM_TAG" \
      --arg to "$TO_TAG" \
      '{
        model: "ci-summarizer",
        max_tokens: 1000,
        messages: [
          {
            role: "system",
            content: "Write CHANGELOG.md sections from conventional commits. Group by type (feat, fix, chore, refactor, test, docs, ci, perf). Flag BREAKING CHANGE footers in bold. Use Keep a Changelog format (https://keepachangelog.com). Be concise and user-focused."
          },
          {
            role: "user",
            content: ("Release " + $to + " from " + $from + ":\n\n" + $commits)
          }
        ],
        metadata: {
          tags: ["ci", "changelog"],
          from_tag: $from,
          to_tag: $to,
          project: "{{PROJECT_NAME}}"
        }
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

    # Extract and output changelog content only
    echo "$RESPONSE" | jq -r '.choices[0].message.content'

# Generate and append changelog to CHANGELOG.md file
changelog-save from_tag="" to_tag="":
    #!/usr/bin/env bash
    set -euo pipefail

    # Determine FROM and TO tags
    FROM_TAG="{{from_tag}}"
    TO_TAG="{{to_tag}}"

    if [ -z "$FROM_TAG" ]; then
        FROM_TAG=$(git describe --tags --abbrev=0 HEAD~1 2>/dev/null || echo "")
        if [ -z "$FROM_TAG" ]; then
            echo -e "{{ RED }}❌ No previous tag found. Specify manually: just changelog-save <from_tag> <to_tag>{{ NC }}"
            exit 1
        fi
    fi

    if [ -z "$TO_TAG" ]; then
        TO_TAG="HEAD"
    fi

    echo -e "{{ BLUE }}📝 Generating changelog from $FROM_TAG to $TO_TAG...{{ NC }}"

    # Generate changelog (only content, no status messages)
    CHANGELOG=$(just _changelog-generate "$FROM_TAG" "$TO_TAG")

    if [ -z "$CHANGELOG" ]; then
        echo -e "{{ RED }}❌ Failed to generate changelog{{ NC }}"
        exit 1
    fi

    # Create or update CHANGELOG.md
    TEMP_FILE=$(mktemp)

    if [ -f "CHANGELOG.md" ]; then
        # Append to existing file (insert after header and description)
        if grep -q "^# Changelog" CHANGELOG.md; then
            # Insert after the header block, before first version section
            # Save new changelog to temp file first to avoid awk multiline issues
            NEW_CONTENT_FILE=$(mktemp)
            echo "$CHANGELOG" > "$NEW_CONTENT_FILE"

            awk -v newfile="$NEW_CONTENT_FILE" '
                BEGIN { inserted = 0 }
                # When we find the first version section and havent inserted yet
                !inserted && /^## \[/ {
                    print ""
                    # Read and print the new content from file
                    while ((getline line < newfile) > 0) {
                        print line
                    }
                    close(newfile)
                    print ""
                    inserted = 1
                }
                # Print every line
                { print }
            ' CHANGELOG.md > "$TEMP_FILE"

            rm -f "$NEW_CONTENT_FILE"
        else
            # No header found, prepend
            echo "$CHANGELOG" > "$TEMP_FILE"
            echo "" >> "$TEMP_FILE"
            cat CHANGELOG.md >> "$TEMP_FILE"
        fi
    else
        # Create new file
        echo "# Changelog" > "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        echo "All notable changes to this project will be documented in this file." >> "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        echo "The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)," >> "$TEMP_FILE"
        echo "and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)." >> "$TEMP_FILE"
        echo "" >> "$TEMP_FILE"
        echo "$CHANGELOG" >> "$TEMP_FILE"
    fi

    mv "$TEMP_FILE" CHANGELOG.md

    echo -e "{{ GREEN }}✅ Changelog saved to CHANGELOG.md{{ NC }}"

# ============================================
# COST REPORTING: Langfuse AI Cost Tracking
# ============================================

# Report AI costs for current run from Langfuse
ai-cost minutes="5":
    #!/usr/bin/env bash
    set -euo pipefail

    PUBLIC_KEY="${LANGFUSE_PUBLIC_KEY:-}"
    SECRET_KEY="${LANGFUSE_SECRET_KEY:-}"

    if [ -z "$PUBLIC_KEY" ] || [ -z "$SECRET_KEY" ]; then
        echo -e "{{ RED }}❌ Langfuse credentials not configured{{ NC }}" >&2
        echo "   Set LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY" >&2
        exit 1
    fi

    # Calculate timestamp from N minutes ago
    if command -v gdate &> /dev/null; then
        # macOS with GNU coreutils installed
        FROM_TIMESTAMP=$(gdate -u -d "{{minutes}} minutes ago" +"%Y-%m-%dT%H:%M:%S.000Z")
    elif [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS native date
        FROM_TIMESTAMP=$(date -u -v-{{minutes}}M +"%Y-%m-%dT%H:%M:%S.000Z")
    else
        # Linux
        FROM_TIMESTAMP=$(date -u -d "{{minutes}} minutes ago" +"%Y-%m-%dT%H:%M:%S.000Z")
    fi

    # Encode credentials for Basic auth
    AUTH=$(printf '%s:%s' "$PUBLIC_KEY" "$SECRET_KEY" | base64)

    # Query Langfuse API for traces in the time window
    RESPONSE=$(curl -s -X GET "http://localhost:8000/api/traces?fromTimestamp=${FROM_TIMESTAMP}" \
      -H "Authorization: Basic $AUTH" \
      -H "Content-Type: application/json" 2>/dev/null || echo '{"data":[]}')

    # Extract and sum costs from all traces
    TOTAL_COST=$(echo "$RESPONSE" | jq '[.data[]?.cost // empty] | add // 0' 2>/dev/null || echo "0")

    # Format output as simple text
    if (( $(echo "$TOTAL_COST > 0" | bc -l 2>/dev/null || echo "0") )); then
        printf "\$%.4f\n" "$TOTAL_COST"
    else
        echo "0.0000"
    fi