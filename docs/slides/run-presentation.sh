#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Claude Code Deep Dive — presenterm launcher
# ─────────────────────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SLIDES="$SCRIPT_DIR/claude-code-deepdive.md"
CONFIG="$SCRIPT_DIR/presenterm-config.yaml"

# ── Install presenterm if missing ────────────────────────────────────────────
if ! command -v presenterm &>/dev/null; then
  echo "📦 Installing presenterm..."
  if command -v brew &>/dev/null; then
    brew install presenterm
  elif command -v cargo &>/dev/null; then
    cargo install presenterm
  else
    echo "❌ Neither brew nor cargo found."
    echo "   Install from: https://github.com/mfontanini/presenterm/releases"
    exit 1
  fi
fi

# ── Font size tip ─────────────────────────────────────────────────────────────
echo ""
echo "  💡 Font size tip (iTerm2 / kitty / WezTerm / Ghostty)"
echo "     These slides use <!-- font_size: 2 --> on title/outro slides."
echo "     For best results: set your terminal font to 14–16pt before starting."
echo "     Increase with: Cmd+= (iTerm2) or Ctrl+= (most others)"
echo ""
echo "  🔑 Navigation: → / Space = next  ·  ← = back  ·  q = quit"
echo ""
read -r -p "  Press Enter to start the presentation..." _

# ── Launch with config (transitions + centering) ─────────────────────────────
PRESENTERM_CONFIG_FILE="$CONFIG" presenterm "$SLIDES"
