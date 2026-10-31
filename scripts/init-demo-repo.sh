#!/usr/bin/env sh
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT/dev-repositories/example"
if [ ! -d "$ROOT/dev-repositories/example/.git" ]; then
  git -C "$ROOT/dev-repositories/example" init
fi
echo "Register the repository in the UI using /workspace/repos/example (Docker) or $(realpath "$ROOT/dev-repositories/example") (native backend)."
