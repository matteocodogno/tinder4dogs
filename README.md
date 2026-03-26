# Tinder for Dogs 🐕❤️

A Spring Boot application that matches dogs based on compatibility scores. Built with Kotlin and designed as part of the AI4Dev course to demonstrate AI-assisted development workflows.

## Overview

This project implements a dog matching service that calculates compatibility between dogs based on various factors including age, breed, gender, and shared preferences. It's built with modern Spring Boot practices and includes AI-powered development tools for code review, testing, and commit message generation.

## Features

- **Dog Matching Service**: Calculate compatibility scores between dogs
- **AI-Powered Development**: Integrated AI tools for code review, test generation, and commit messages
- **LiteLLM Integration**: Use multiple AI providers through a unified API
- **Langfuse Observability**: Track and monitor AI usage and costs
- **PostgreSQL Database**: Persistent data storage with Docker Compose

## Tech Stack

- **Language**: Kotlin 2.2.21
- **Framework**: Spring Boot 4.0.4
- **JDK**: Java 24
- **Database**: PostgreSQL
- **Build Tool**: Maven
- **Task Runner**: Just
- **Version Management**: mise
- **AI Infrastructure**: LiteLLM, Langfuse

## Prerequisites

Before starting, ensure you have the following tools installed:

- **mise** - Version manager
- **Docker** - Container runtime
- **Git** - Version control
- **just** - Command runner
- **direnv** - Environment variable management
- **gitleaks** - Secret scanning
- **Infisical or 1Password CLI** (optional) - Secret management

Install all prerequisites:

```bash
brew install mise docker git just direnv gitleaks
brew install infisical/get-cli/infisical  # or 1password-cli
```

## Environment Setup

1. **Configure mise** (activates automatically when entering directory):
```bash
mise install
```

2. **Set up environment variables**:

Required environment variables:
- `LITELLM_MASTER_KEY` - LiteLLM API key
- `OPENAI_API_KEY` - OpenAI API key
- `ANTHROPIC_API_KEY` - Anthropic API key
- `LANGFUSE_PUBLIC_KEY` - Langfuse public key
- `LANGFUSE_SECRET_KEY` - Langfuse secret key
- `GEMINI_API_KEY` (optional) - Google Gemini API key

Configure direnv and allow the `.envrc` file:
```bash
direnv allow .
```

3. **Verify setup**:
```bash
just check-tools  # Check all required tools
just check-env    # Verify environment variables
```

## Getting Started

### Initial Setup

Run the complete setup process:

```bash
just setup
```

This will:
- Start AI infrastructure (LiteLLM, Langfuse)
- Install mise-managed tools
- Build the project
- Create necessary directories

### Running the Application

```bash
# Start the application
just run

# Run in development mode with auto-reload
just dev
```

### Building

```bash
# Build with tests
just build

# Build without tests (faster)
just build-fast

# Package as JAR
just package
```

### Testing

```bash
# Run all tests
just test

# Run specific test class
just test-class ClassName

# Run tests with coverage report
just test-coverage
```

## AI-Powered Development

This project includes AI-powered tools for various development tasks:

### Code Review

Review any file with AI-powered analysis:

```bash
just review src/main/kotlin/com/ai4dev/tinderfordogs/match/service/DogMatcherService.kt
```

Reviews are saved to `reviews/` directory with timestamps.

### Test Generation

Automatically generate tests for any file:

```bash
just gen-tests src/main/kotlin/com/ai4dev/tinderfordogs/match/service/DogMatcherService.kt
```

Generated tests use JUnit 5, MockK, and Spring Boot Test.

### Commit Message Generation

Generate conventional commit messages from staged changes:

```bash
# Generate and commit interactively
just commit

# Just generate the message
just commit-msg
```

## AI Infrastructure Management

### Start/Stop Services

```bash
# Start all AI services
just ai-start

# Stop all AI services
just ai-stop

# Restart services
just ai-restart

# Check service status
just ai-status
```

### Monitor Usage

```bash
# View service logs
just ai-logs litellm

# Check AI usage costs
just ai-costs

# Test LiteLLM connection
just ai-test
```

## Project Structure

```
tinder-for-dogs/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   └── com/ai4dev/tinderfordogs/
│   │   │       ├── match/
│   │   │       │   ├── model/
│   │   │       │   │   └── Dog.kt
│   │   │       │   └── service/
│   │   │       │       └── DogMatcherService.kt
│   │   │       └── TinderForDogsApplication.kt
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
│       └── kotlin/
├── prompts/
│   └── templates/         # AI prompt templates
├── reviews/              # Generated code reviews
├── compose.yaml          # Docker Compose configuration
├── Justfile             # Task definitions
├── pom.xml              # Maven configuration
└── .mise.toml           # Tool version management
```

## Core Components

### Dog Model
Represents a dog with properties: id, name, breed, age, gender, and preferences.

### DogMatcherService
Service that calculates compatibility scores between dogs and finds best matches:
- `calculateCompatibility(dog1, dog2)` - Returns compatibility score (0-1)
- `findBestMatch(dog, candidates)` - Finds best match from candidates

## Available Commands

Get a full list of available commands:

```bash
just          # List all commands
just help     # Detailed help with examples
```

### Common Commands

| Command | Description |
|---------|-------------|
| `just setup` | Complete initial setup |
| `just run` | Start the application |
| `just test` | Run tests |
| `just build` | Build the project |
| `just clean` | Clean build artifacts |
| `just ai-start` | Start AI services |
| `just review <file>` | Review code with AI |
| `just gen-tests <file>` | Generate tests |
| `just commit` | AI-assisted commit |

## Development Workflow

1. **Start AI services**: `just ai-start`
2. **Make changes**: Edit code
3. **Review code**: `just review <file>`
4. **Generate tests**: `just gen-tests <file>`
5. **Run tests**: `just test`
6. **Commit**: `just commit`

## Configuration

### Maven
Configuration in `pom.xml` includes:
- Spring Boot 4.0.4
- Kotlin 2.2.21
- JaCoCo for code coverage
- Spring Boot DevTools for auto-reload

### Database
PostgreSQL runs via Docker Compose:
- Host: localhost
- Port: 5432
- Database: mydatabase
- User: myuser
- Password: secret

## Troubleshooting

### Tools Not Found
```bash
just check-tools
```

### Environment Variables Missing
```bash
just check-env
```

### Mise Not Activated
```bash
cd .  # Re-enter directory to trigger mise
```

### AI Services Not Running
```bash
just ai-status
just ai-restart
```

## Contributing

1. Make your changes
2. Run tests: `just test`
3. Review code: `just review <file>`
4. Commit with AI: `just commit`

## License

Part of the AI4Dev course project.

## Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [LiteLLM Documentation](https://docs.litellm.ai/)
- [Langfuse Documentation](https://langfuse.com/docs)
- [Just Command Runner](https://github.com/casey/just)
