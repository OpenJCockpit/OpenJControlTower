# Browser end-to-end tests

Playwright tests that drive the real application through Chromium:

- **`tests/authorization.e2e.ts`:**
  - Keycloak login redirect
  - rejected credentials
  - `401` without a token
  - viewer role is read-only (`403` on writes)
  - sign-out
- **`tests/discovery.e2e.ts`:**
  - workspace path confinement
  - the complete editor flow: repository → interview (with follow-up) → spec generation → approval (files asserted on the workspace volume) → DAG readiness → dispatch → feature interview

## Run

```bash
sh scripts/run-e2e.sh
```

Only Docker is required. The script builds the backend and frontend images, starts `docker-compose.e2e.yml` (PostgreSQL, Keycloak with the test-only realm in `keycloak/`, backend and frontend), waits for their health checks, runs Playwright in its official image, and always removes the stack and its volumes. The results go to `playwright-report/` (HTML) and `test-results/` (traces and screenshots of failures).

To iterate on tests against a running stack:

```bash
cd e2e
docker compose -f docker-compose.e2e.yml up -d --build --wait postgres keycloak backend frontend
docker compose -f docker-compose.e2e.yml --profile test run --rm --build e2e npx playwright test -g "viewer"
docker compose -f docker-compose.e2e.yml --profile test down -v
```

The state persists between runs on a running stack. The main flow expects a fresh database and workspace, so run `down -v` before repeating it.

## Notes

- **Test-only credentials:** `e2e-editor` / `e2e-viewer` exist only in `keycloak/e2e-realm.json`. Never import that realm anywhere else.
- **Secure context:** the browser uses Compose hostnames over HTTP. keycloak-js needs Web Crypto, so `playwright.config.ts` runs full Chromium with `--unsafely-treat-insecure-origin-as-secure` for exactly `http://frontend:8080` and `http://keycloak:8080`.
- **Versions:** keep `@playwright/test` in `package.json` equal to the image tag in `Dockerfile`. Renovate groups them.
