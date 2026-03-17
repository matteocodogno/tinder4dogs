# Justfile for Tinder for Dogs - Complete Edition
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
    @echo ""
    @echo "{{ BLUE }}AI Commands:{{ NC }}"
    @echo "  just review <file>      - Review code (GPT-4)"
    @echo "  just write-feature <name> <desc> - Generate feature (Claude)"
    @echo "  just gen-tests <file>   - Generate tests (GPT-4)"
    @echo "  just commit             - AI-generated commit message"
    @echo ""
    @echo "{{ BLUE }}Examples:{{ NC }}"
    @echo "  just setup"
    @echo "  just write-feature DogMatching 'Match dogs based on preferences'"
    @echo "  just review src/main/kotlin/service/DogService.kt"
    @echo ""

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

# Lint Kotlin sources with ktlint
lint: check-mise
    @echo "{{ BLUE }}🔎 Running ktlint check...{{ NC }}"
    @./mvnw ktlint:check
    @echo "{{ GREEN }}✅ Lint passed{{ NC }}"

# Auto-fix ktlint violations
lint-fix: check-mise
    @echo "{{ YELLOW }}🔧 Running ktlint format...{{ NC }}"
    @./mvnw ktlint:format
    @echo "{{ GREEN }}✅ Lint fixes applied{{ NC }}"

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
        # Append to existing file (insert after header)
        if grep -q "^# Changelog" CHANGELOG.md; then
            # Insert after the "# Changelog" line
            awk -v new="$CHANGELOG" '
                /^# Changelog/ {
                    print
                    if (getline > 0) print
                    print ""
                    print new
                    print ""
                    next
                }
                {print}
            ' CHANGELOG.md > "$TEMP_FILE"
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

# Test local embedding
test-embed text="Hello world":
    #!/usr/bin/env bash
    curl -s http://localhost:4000/embeddings \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer {{LITELLM_KEY}}" \
      -d '{"model":"local-embed","input":"{{text}}"}' \
      | jq '{model:.model, dimensions:(.data[0].embedding|length), first_5:.data[0].embedding[:5]}'

## ============================================
## ROLE: WRITER (Claude - Code Generation)
## ============================================
#
## Generate code from specification file
#write-code spec:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}✍️  Claude (Writer) is generating code...{{ NC }}"
#
#    if [ ! -f "{{spec}}" ]; then
#        echo -e "{{ RED }}❌ File not found: {{spec}}{{ NC }}"
#        exit 1
#    fi
#
#    SPEC_CONTENT=$(cat {{spec}})
#    TEMPLATE=$(cat prompts/templates/code_generation.md)
#
#    PROMPT=$(echo "$TEMPLATE" | \
#      sed "s|{{SPECIFICATION}}|$SPEC_CONTENT|g" | \
#      sed "s|{{LANGUAGE}}|kotlin|g" | \
#      sed "s|{{CONTEXT}}|Tinder for Dogs - Spring Boot app|g")
#
#    just _call_ai writer "$PROMPT" task=code_generation language=kotlin

## Generate complete feature (service + controller + tests)
#write-feature name description:
#    #!/usr/bin/env bash
#    echo -e "{{ GREEN }}✍️  Generating feature: {{name}}{{ NC }}"
#
#    PROMPT="Generate a complete Kotlin Spring Boot feature for: {{name}}
#
#Description: {{description}}
#
#Requirements:
#- Create a Service class with business logic
#- Create a REST Controller with proper endpoints
#- Use Spring Boot 4.x conventions
#- Add proper error handling and validation
#- Include OpenAPI/Swagger annotations
#- Follow REST best practices
#- Make it production-ready
#
#Project context: Tinder for Dogs dating app
#
#Output the code for both Service and Controller."
#
#    OUTPUT=$(just _call_ai writer "$PROMPT" task=feature_generation feature={{name}})
#
#    # Create directory
#    FEATURE_DIR="src/main/kotlin/com/ai4dev/tinderfordogs/feature/$(echo {{name}} | tr '[:upper:]' '[:lower:]')"
#    mkdir -p "$FEATURE_DIR"
#
#    # Save output
#    echo "$OUTPUT" > "$FEATURE_DIR/{{name}}Feature.kt"
#
#    echo -e "{{ GREEN }}✅ Feature created at $FEATURE_DIR/{{name}}Feature.kt{{ NC }}"
#    echo -e "{{ BLUE }}💡 Next: just review $FEATURE_DIR/{{name}}Feature.kt{{ NC }}"

## Quick review (no file save)
#review-quick file:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}🔍 Quick review of {{file}}...{{ NC }}"
#
#    if [ ! -f "{{file}}" ]; then
#        echo -e "{{ RED }}❌ File not found: {{file}}{{ NC }}"
#        exit 1
#    fi
#
#    CODE=$(cat {{file}})
#
#    PROMPT="Quick code review of this Kotlin code. Focus on:
#- Critical bugs or errors
#- Security vulnerabilities
#- Performance issues
#- Major improvements needed
#
#\`\`\`kotlin
#$CODE
#\`\`\`
#
#Provide a concise summary (3-5 bullet points)."
#
#    just _call_ai reviewer "$PROMPT" task=quick_review file={{file}}
#
## Security-focused review
#review-security file:
#    #!/usr/bin/env bash
#    echo -e "{{ RED }}🔒 Security review of {{file}}{{ NC }}"
#
#    if [ ! -f "{{file}}" ]; then
#        echo -e "{{ RED }}❌ File not found: {{file}}{{ NC }}"
#        exit 1
#    fi
#
#    CODE=$(cat {{file}})
#
#    PROMPT="Security audit of this Kotlin Spring Boot code:
#
#\`\`\`kotlin
#$CODE
#\`\`\`
#
#Check for:
#1. SQL injection vulnerabilities
#2. XSS attacks (if web-facing)
#3. Authentication/authorization issues
#4. Sensitive data exposure (passwords, tokens, PII)
#5. Input validation
#6. CSRF protection
#7. Insecure dependencies
#
#Provide:
#- Severity (HIGH/MEDIUM/LOW)
#- Specific line numbers if possible
#- Recommended fixes"
#
#    just _call_ai reviewer "$PROMPT" task=security_review file={{file}}
#
## Review all files in a package
#review-package package:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}🔍 Reviewing package: {{package}}{{ NC }}"
#
#    FILES=$(find src/main/kotlin -path "*{{package}}*" -name "*.kt")
#
#    if [ -z "$FILES" ]; then
#        echo -e "{{ RED }}❌ No Kotlin files found in package: {{package}}{{ NC }}"
#        exit 1
#    fi
#
#    for file in $FILES; do
#        echo ""
#        echo -e "{{ YELLOW }}Reviewing: $file{{ NC }}"
#        just review-quick "$file"
#        echo ""
#    done
#
## ============================================
## ROLE: ARCHITECT (Gemini - Design & Docs)
## ============================================
#
## Design architecture from requirements
#design-arch requirements_file:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}🏗️  Gemini (Architect) is designing architecture...{{ NC }}"
#
#    if [ ! -f "{{requirements_file}}" ]; then
#        echo -e "{{ RED }}❌ File not found: {{requirements_file}}{{ NC }}"
#        exit 1
#    fi
#
#    REQUIREMENTS=$(cat {{requirements_file}})
#    TEMPLATE=$(cat prompts/templates/architecture_design.md)
#
#    PROMPT=$(echo "$TEMPLATE" | \
#      sed "s|{{REQUIREMENTS}}|$REQUIREMENTS|g" | \
#      sed "s|{{CONSTRAINTS}}|Spring Boot 3.x, Kotlin, PostgreSQL, REST API|g" | \
#      sed "s|{{EXISTING}}|Existing: Dog entity, basic matching service|g")
#
#    mkdir -p docs/architecture
#    OUTPUT_FILE="docs/architecture/design-$(date +%Y%m%d-%H%M%S).md"
#
#    just _call_ai architect "$PROMPT" task=architecture_design | tee "$OUTPUT_FILE"
#
#    echo ""
#    echo -e "{{ GREEN }}✅ Architecture design saved to: $OUTPUT_FILE{{ NC }}"
#
## Generate API documentation
#gen-docs file:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}📚 Generating documentation for {{file}}...{{ NC }}"
#
#    if [ ! -f "{{file}}" ]; then
#        echo -e "{{ RED }}❌ File not found: {{file}}{{ NC }}"
#        exit 1
#    fi
#
#    CODE=$(cat {{file}})
#
#    PROMPT="Generate comprehensive API documentation for this Kotlin Spring Boot code:
#
#\`\`\`kotlin
#$CODE
#\`\`\`
#
#Include:
#1. Overview and purpose
#2. Endpoint descriptions (if controller)
#3. Request/response examples with JSON
#4. HTTP status codes
#5. Error responses
#6. Authentication requirements
#7. Example curl commands
#
#Format in Markdown."
#
#    mkdir -p docs/api
#    OUTPUT_FILE="docs/api/$(basename {{file}} .kt)-api.md"
#
#    just _call_ai architect "$PROMPT" task=documentation file={{file}} | tee "$OUTPUT_FILE"
#
#    echo ""
#    echo -e "{{ GREEN }}✅ Documentation saved to: $OUTPUT_FILE{{ NC }}"
#
## Generate project README
#gen-readme:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}📝 Generating project README...{{ NC }}"
#
#    # Gather project info
#    STRUCTURE=$(find src/main/kotlin -name "*.kt" | head -20 | sed 's/^/    /')
#    POM_DEPS=$(cat pom.xml | grep -A 50 "<dependencies>" | head -50)
#
#    PROMPT="Generate a comprehensive README.md for this Spring Boot project:
#
#**Project:** Tinder for Dogs - A dating app for dogs
#**Tech Stack:** Kotlin, Spring Boot 3.x, PostgreSQL, Maven
#**Purpose:** AI4Dev training course project
#
#**File Structure:**
#$STRUCTURE
#
#**Key Dependencies:**
#$POM_DEPS
#
#**Generate README with:**
#1. Project title and description
#2. Features overview
#3. Prerequisites (Java 17, Maven, Docker)
#4. Setup instructions (step-by-step)
#5. Running the application
#6. API endpoints (if you can infer them)
#7. Testing
#8. Project structure
#9. AI integration (LiteLLM + Langfuse)
#10. Contributing guidelines
#
#Use badges, emojis, and proper Markdown formatting."
#
#    just _call_ai architect "$PROMPT" task=readme_generation > README.md
#
#    echo -e "{{ GREEN }}✅ README.md created{{ NC }}"

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

## Generate tests for entire package
#gen-tests-package package:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}🧪 Generating tests for package: {{package}}{{ NC }}"
#
#    FILES=$(find src/main/kotlin -path "*{{package}}*" -name "*.kt" ! -path "*/test/*")
#
#    if [ -z "$FILES" ]; then
#        echo -e "{{ RED }}❌ No files found in package: {{package}}{{ NC }}"
#        exit 1
#    fi
#
#    for file in $FILES; do
#        echo ""
#        echo -e "{{ YELLOW }}Generating tests for: $file{{ NC }}"
#        just gen-tests "$file"
#    done
#
#    echo ""
#    echo -e "{{ GREEN }}✅ All tests generated{{ NC }}"

## ============================================
## ROLE: REFACTORER (Claude - Refactoring)
## ============================================
#
## Refactor code with specific goal
#refactor file goal:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}♻️  Claude (Refactorer) is refactoring {{file}}...{{ NC }}"
#
#    if [ ! -f "{{file}}" ]; then
#        echo -e "{{ RED }}❌ File not found: {{file}}{{ NC }}"
#        exit 1
#    fi
#
#    CODE=$(cat {{file}})
#    TEMPLATE=$(cat prompts/templates/refactor.md)
#
#    PROMPT=$(echo "$TEMPLATE" | \
#      sed "s|{{CODE}}|$CODE|g" | \
#      sed "s|{{GOAL}}|{{goal}}|g" | \
#      sed "s|{{LANGUAGE}}|kotlin|g")
#
#    # Backup original
#    cp {{file}} {{file}}.backup
#
#    # Get refactored code
#    REFACTORED=$(just _call_ai refactorer "$PROMPT" task=refactoring goal="{{goal}}")
#
#    # Write refactored code
#    echo "$REFACTORED" > {{file}}
#
#    echo -e "{{ GREEN }}✅ Refactored {{file}}{{ NC }}"
#    echo -e "{{ BLUE }}💾 Backup saved at {{file}}.backup{{ NC }}"
#    echo -e "{{ YELLOW }}💡 Review changes with: git diff {{file}}{{ NC }}"
#
## Apply design pattern
#apply-pattern file pattern:
#    @just refactor {{file}} "Apply {{pattern}} pattern"
#
## Remove code smells
#fix-smells file:
#    @just refactor {{file}} "Remove code smells and improve readability"
#
## ============================================
## Combined Workflows
## ============================================
#
## Complete feature development cycle
#feature-complete name description:
#    #!/usr/bin/env bash
#    echo -e "{{ GREEN }}🚀 Complete feature development for: {{name}}{{ NC }}"
#    echo ""
#
#    # 1. Generate feature
#    echo -e "{{ BLUE }}Step 1/4: Generating code...{{ NC }}"
#    just write-feature {{name}} "{{description}}"
#
#    FEATURE_DIR="src/main/kotlin/com/ai4dev/tinderfordogs/feature/$(echo {{name}} | tr '[:upper:]' '[:lower:]')"
#    FILE="$FEATURE_DIR/{{name}}Feature.kt"
#
#    # 2. Review
#    echo ""
#    echo -e "{{ BLUE }}Step 2/4: Reviewing code...{{ NC }}"
#    just review "$FILE"
#
#    # 3. Generate tests
#    echo ""
#    echo -e "{{ BLUE }}Step 3/4: Generating tests...{{ NC }}"
#    just gen-tests "$FILE"
#
#    # 4. Run tests
#    echo ""
#    echo -e "{{ BLUE }}Step 4/4: Running tests...{{ NC }}"
#    just test
#
#    echo ""
#    echo -e "{{ GREEN }}✅ Feature complete: {{name}}{{ NC }}"
#    echo -e "{{ BLUE }}💡 Next: just commit{{ NC }}"
#
## Pre-commit workflow
#pre-commit:
#    #!/usr/bin/env bash
#    echo -e "{{ BLUE }}🔍 Running pre-commit checks...{{ NC }}"
#    echo ""
#
#    # 1. Security scan
#    echo -e "{{ YELLOW }}1. Security scan...{{ NC }}"
#    if gitleaks detect --source . --verbose --no-git; then
#        echo -e "{{ GREEN }}✅ No secrets detected{{ NC }}"
#    else
#        echo -e "{{ RED }}❌ Secrets detected! Fix before committing.{{ NC }}"
#        exit 1
#    fi
#
#    echo ""
#
#    # 2. Quick review of staged files
#    echo -e "{{ YELLOW }}2. Quick review of staged files...{{ NC }}"
#    STAGED_FILES=$(git diff --staged --name-only --diff-filter=ACM | grep "\.kt$" || true)
#
#    if [ -n "$STAGED_FILES" ]; then
#        for file in $STAGED_FILES; do
#            if [ -f "$file" ]; then
#                echo "  Reviewing: $file"
#                just review-quick "$file" | head -10
#            fi
#        done
#    else
#        echo "  No Kotlin files staged"
#    fi
#
#    echo ""
#
#    # 3. Run tests
#    echo -e "{{ YELLOW }}3. Running tests...{{ NC }}"
#    if just test; then
#        echo -e "{{ GREEN }}✅ Tests passed{{ NC }}"
#    else
#        echo -e "{{ RED }}❌ Tests failed!{{ NC }}"
#        exit 1
#    fi
#
#    echo ""
#    echo -e "{{ GREEN }}✅ Pre-commit checks passed{{ NC }}"
#    echo -e "{{ BLUE }}💡 Ready to commit: just commit{{ NC }}"
#
## ============================================
## Utilities
## ============================================
#
## Show available AI roles
#roles:
#    @echo -e "{{ BLUE }}🤖 AI Roles in AI4Dev:{{ NC }}"
#    @echo ""
#    @echo "  {{ GREEN }}writer{{ NC }}     → Claude Sonnet    → Code generation"
#    @echo "  {{ GREEN }}reviewer{{ NC }}   → GPT-4 Turbo      → Code review"
#    @echo "  {{ GREEN }}architect{{ NC }}  → Gemini 2.0       → Architecture & docs"
#    @echo "  {{ GREEN }}tester{{ NC }}     → GPT-4 Turbo      → Test generation"
#    @echo "  {{ GREEN }}committer{{ NC }}  → GPT-4 Mini       → Commit messages"
#    @echo "  {{ GREEN }}refactorer{{ NC }} → Claude Sonnet    → Code refactoring"
#    @echo ""
#    @echo -e "{{ BLUE }}💡 Use 'just ai-costs' to see cost breakdown{{ NC }}"
#
## Open Langfuse dashboard
#langfuse:
#    @open http://localhost:3000
#
## Open Langfuse filtered by role
#langfuse-role role:
#    @open "http://localhost:3000/project/{{LANGFUSE_PROJECT}}/traces?filter=role:{{role}}"
#
## Scan for secrets
#scan-secrets:
#    @echo -e "{{ BLUE }}🔍 Scanning for secrets...{{ NC }}"
#    @gitleaks detect --source . --verbose
#
## Show project info
#info:
#    @echo -e "{{ BLUE }}📊 Project Information:{{ NC }}"
#    @echo ""
#    @echo "Project: {{PROJECT_NAME}}"
#    @echo "Langfuse Project: {{LANGFUSE_PROJECT}}"
#    @echo ""
#    @echo "Environment:"
#    @just check-env 2>/dev/null || echo "  (run 'just check-env' for details)"
#    @echo ""
#    @echo "Services:"
#    @just ai-status 2>/dev/null || echo "  (run 'just ai-start' to start)"
#
## Clean everything (build + Docker volumes)
#clean-all: clean
#    @echo -e "{{ YELLOW }}🧹 Cleaning Docker volumes...{{ NC }}"
#    @cd ~/.ai && docker-compose down -v
#    @echo -e "{{ GREEN }}✅ Everything cleaned{{ NC }}"
