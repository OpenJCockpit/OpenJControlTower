# API end-to-end tests

Playwright tests that call the real backend over HTTP with access tokens from a real Keycloak (test-only password grant). No browser is involved.

- **`tests/authorization.e2e.ts`:**
  - rejected credentials
  - `401` without a token or with a malformed one
  - public health probe
  - viewer role is read-only (`403` on writes)
- **`tests/discovery.e2e.ts`:**
  - Git URLs with embedded credentials are refused
  - the complete editor flow: repository with credentials (connection check with a wrong and a right password; the password stays write-only on edit and the check reuses it) → interview (with follow-up) → spec generation → approval (the pushed branch is cloned, and its files and five commits are checked) → DAG readiness → dispatch → feature interview
- **`tests/settings.e2e.ts`:**
  - an administrator tests and saves the policy agent; an unreachable agent is refused
  - other users see the settings read-only
  - a policy refuses a repository on a Git host that is not allowed (`opa-config.json` allows only `git-server`)

## Run

```bash
sh scripts/run-e2e.sh
```

Only Docker is required. The script builds the backend image, starts `docker-compose.e2e.yml` (PostgreSQL, Keycloak with the test-only realm in `keycloak/`, a Git server, Open Policy Agent with the test values in `opa-config.json`, a Docker socket proxy and the backend), waits for their health checks, runs Playwright in its official image, and always removes the stack and its volumes. The results go to `playwright-report/` (HTML) and `test-results/` (traces of failures).

To iterate on tests against a running stack:

```bash
cd e2e
docker compose -f docker-compose.e2e.yml up -d --build --wait postgres keycloak git-server opa docker-proxy backend
docker compose -f docker-compose.e2e.yml --profile test run --rm --build e2e npx playwright test -g "viewer"
docker compose -f docker-compose.e2e.yml --profile test down -v
```

The state persists between runs on a running stack. The main flow expects a fresh database and Git server, so run `down -v` before repeating it.

## Notes

- **Test-only credentials:** `e2e-editor` / `e2e-viewer` / `e2e-admin` exist only in `keycloak/e2e-realm.json`. Never import that realm anywhere else. The Git server login `e2e-git` and the credential encryption key exist only in `docker-compose.e2e.yml`.
- **Docker access:** the backend starts Git job containers through a Docker socket proxy, on the fixed network `ojct-e2e`. `scripts/run-e2e.sh` fails if any of them is left behind.
- **Versions:** keep `@playwright/test` in `package.json` equal to the image tag in `Dockerfile`. Renovate groups them.
