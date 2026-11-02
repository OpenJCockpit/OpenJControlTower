#!/bin/sh
# Browser end-to-end tests: builds the images, starts a disposable stack (PostgreSQL, Keycloak with a
# test-only realm, a Git server, Open Policy Agent, a Docker socket proxy, backend, frontend) and runs Playwright in its
# official container. Always cleans up.
set -eu
cd "$(dirname "$0")/../e2e"
compose() { docker compose -f docker-compose.e2e.yml "$@"; }
cleanup() { compose --profile test down -v --remove-orphans >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM
cleanup
rm -rf playwright-report test-results
compose up -d --build --wait --wait-timeout 300 postgres keycloak git-server opa docker-proxy backend frontend
status=0
compose --profile test run --rm --build e2e || status=$?
# The Git harness must leave nothing behind: every job container is removed after its push.
leftover=$(docker ps -aq --filter label=openjcontroltower.owner=ojct-e2e)
if [ -n "$leftover" ]; then
  echo "Git job containers were not removed: $leftover" >&2
  docker rm -f $leftover >/dev/null 2>&1 || true
  status=1
fi
if [ "$status" -ne 0 ]; then
  echo "E2E tests failed; recent backend and Keycloak logs:" >&2
  compose logs --tail 80 backend keycloak >&2 || true
  echo "HTML report: e2e/playwright-report/index.html" >&2
fi
exit "$status"
