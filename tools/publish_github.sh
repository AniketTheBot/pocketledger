#!/usr/bin/env bash
# Creates the GitHub repo and pushes main + tags. Requires: gh auth login (once).
set -euo pipefail
cd "$(dirname "$0")/.."

if ! gh auth status >/dev/null 2>&1; then
  echo "✗ GitHub CLI is not authenticated."
  echo "  Run: gh auth login"
  echo "  Then re-run: tools/publish_github.sh"
  exit 1
fi

if git remote get-url origin >/dev/null 2>&1; then
  echo "▸ origin exists — pushing"
  git push -u origin main --follow-tags
else
  echo "▸ creating private repo 'pocketledger' and pushing"
  gh repo create pocketledger --private --source=. --remote=origin --push
  git push origin --tags
fi

echo "✓ published: $(gh repo view --json url -q .url)"
