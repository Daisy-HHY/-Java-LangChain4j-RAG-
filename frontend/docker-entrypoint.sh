#!/bin/sh
set -eu

# A named node_modules volume survives rebuilds. Refresh it when dependencies
# or the container's Node version change, without downloading on every start.
dependency_hash="$( { sha256sum package.json package-lock.json; node --version; } | sha256sum | cut -d ' ' -f 1)"
marker=node_modules/.kgqa-dependency-hash
if [ ! -f "$marker" ] || [ "$(cat "$marker")" != "$dependency_hash" ]; then
  npm ci --no-audit --no-fund
  printf '%s\n' "$dependency_hash" > "$marker"
fi

exec "$@"
