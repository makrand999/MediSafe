#!/usr/bin/env bash
# run_muse.sh — Helper script to execute Muse Code in headless --yolo mode with a prompt file.
set -euo pipefail

WORKSPACE_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "${BASH_SOURCE[0]}")/../../../../" && pwd))"
cd "$WORKSPACE_ROOT"

if [ $# -eq 0 ]; then
  echo "Usage: $0 <prompt-file-path> [extra-muse-args...]" >&2
  echo "   or: $0 -m \"<inline-prompt-string>\"" >&2
  exit 1
fi

PROMPT_FILE=""
TEMP_FILE=""

cleanup() {
  if [ -n "$TEMP_FILE" ] && [ -f "$TEMP_FILE" ]; then
    rm -f "$TEMP_FILE"
  fi
}
trap cleanup EXIT

if [ "$1" = "-m" ]; then
  shift
  if [ $# -eq 0 ]; then
    echo "Error: -m requires a prompt string" >&2
    exit 1
  fi
  TEMP_FILE="$(mktemp /tmp/muse_prompt_XXXXXX.md)"
  echo "$1" > "$TEMP_FILE"
  PROMPT_FILE="$TEMP_FILE"
  shift
else
  PROMPT_FILE="$1"
  shift
  if [ ! -f "$PROMPT_FILE" ]; then
    echo "Error: Prompt file not found: $PROMPT_FILE" >&2
    exit 1
  fi
fi

echo "=== [muse-programmer] Launching Muse Code in headless yolo mode ==="
echo "Workspace: $WORKSPACE_ROOT"
echo "Prompt file: $PROMPT_FILE"
echo "=================================================================="

# Run muse exec --yolo with workspace root and prompt file
muse exec --yolo --workspace "$WORKSPACE_ROOT" --prompt-file "$PROMPT_FILE" "$@"

EXIT_CODE=$?
echo ""
echo "=== [muse-programmer] Muse Code execution completed (Exit code: $EXIT_CODE) ==="

# Summarize git diff if git repository exists
if [ -d "$WORKSPACE_ROOT/.git" ]; then
  echo ""
  echo "--- Git Status ---"
  git status --short
  echo ""
  echo "--- Modified Files Summary ---"
  git diff --stat || true
fi

exit $EXIT_CODE
