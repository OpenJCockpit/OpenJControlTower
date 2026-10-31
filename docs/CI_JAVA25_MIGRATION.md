# Java 25 / Spring Boot 4.1 migration & GitHub Actions CI

Updated: 2026-10-31

## Versions

- Java 25 (`.java-version`, Maven compiler release, Maven Enforcer)
- Spring Boot **4.1.2** (latest 4.1 maintenance release as of 2026-10-31)
- Spring AI **2.0.2** (stable, compatible with Boot 4.1)
- springdoc-openapi **3.2.0** (Boot 4.1 compatible)
- JaCoCo **0.8.16** (supports Java 25)
- Docker build stage `maven:3.9-eclipse-temurin-25`, runtime `eclipse-temurin:25-jre-alpine`

## Breaking changes handled

1. Spring Boot 4 starter artifacts: `spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server` and `spring-boot-starter-security-oauth2-client`.
2. Jackson 3 as Boot 4 default. `DataStore` uses the auto-configured `tools.jackson.databind.json.JsonMapper`, new type/exception imports; the optional decision client consumes Jackson 3 `JsonNode`.
3. Spring AI BOM uses a stable 2.x release; the existing `ChatClient` structured-output interface is retained.
4. JVM compile is pinned to release 25, while the Maven Enforcer fails early for mismatching local JDKs.

## GitHub Actions

Workflow: `.github/workflows/ci.yml` on push, pull request and manual invocation.

- **Backend:** JDK 25; `mvn --batch-mode --no-transfer-progress clean verify`; JaCoCo checks 100% line coverage; reports uploaded even on failure.
- **Frontend:** Node 22; `npm install`, `npm run generate:api`, `npm run build`, `npm run test`.
- **Containers:** `docker build` for the backend and frontend independently, so container build issues are visible even when JaCoCo fails.
- **E2E:** `sh scripts/run-e2e.sh` builds the images, starts PostgreSQL, Keycloak (test-only realm `e2e/keycloak/e2e-realm.json`), backend and frontend, waits for health checks, and runs Playwright in `mcr.microsoft.com/playwright`. The report and traces are uploaded; the stack is always removed.
- **Kubernetes manifests:** renders `deploy/kubernetes/overlays/first-run` with `kubectl kustomize`, with and without the `network-policies` component.
- **Publish images:** on pushes to `main` and `v*` tags, and only after all of the above pass (including E2E), pushes `openjcontroltower-backend` and `openjcontroltower-frontend` to `ghcr.io/<owner>/` (tags: branch, semver, `sha-<commit>`) using the built-in `GITHUB_TOKEN`.
- **Renovate config:** `npx --yes --package renovate -- renovate-config-validator --strict` validates the version update policy.
- **Renovate:** `renovate.json` configures dependency update PRs for Maven, npm, GitHub Actions and Docker. The `renovate-config` CI job checks the config syntax/semantics. The previous Dependabot *version-update* config was removed to avoid duplicate update PRs; Dependabot *security alerts* may remain enabled for vulnerability data. See [RENOVATE.md](RENOVATE.md).

GitHub setup: unpack archive, push its files into a GitHub repository, enable GitHub Actions in the repository settings, push a branch or create a PR, and read the job results on the Actions tab. Add branch protection with `backend`, `frontend`, and `containers` required checks **only when those checks are passing**. No extra secrets are needed: image publishing uses `GITHUB_TOKEN` with `packages: write`. The pipeline publishes images but does not deploy them; see [KUBERNETES.md](KUBERNETES.md).

## Known gaps

- `mvn clean verify` passes locally on Temurin 25.0.3 / Maven 3.9.16 with **100% JaCoCo line coverage** (78 tests, no exclusions). The frontend build, typecheck and Vitest suite, and both Docker images, also pass locally. The workflows have not yet run on GitHub, because no remote is connected.
- Spring Security 7 adds `FACTOR_*` authorities (for example `FACTOR_BEARER`) to authenticated JWTs; tests that assert on authorities filter them out.
- The Vitest suite covers the OpenAPI HTTP client and runtime Keycloak configuration. Browser E2E tests (7, Playwright) pass locally against the Compose stack in `e2e/`.
- E2E networking: the browser reaches Compose services over plain HTTP (`http://frontend:8080`, `http://keycloak:8080`). keycloak-js needs Web Crypto for PKCE, which only exists in secure contexts. The suite therefore runs Playwright's full Chromium (`channel: 'chromium'`) with `--unsafely-treat-insecure-origin-as-secure` for exactly those two origins; the default headless shell ignores that flag. The backend's issuer is `http://keycloak:8080/realms/openjcontroltower` to match.
- Docker `package -DskipTests` builds a container but does not prove test correctness. The backend CI job is the quality gate, and image publishing depends on it.

## References

- Spring Boot release: https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/
- Spring AI release: https://spring.io/blog/2026/10/23/spring-ai-2-1-0-RC1-available-now/
- Spring Boot migration guide: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide
- springdoc compatibility: https://springdoc.org/faq.html
- JaCoCo releases: https://www.jacoco.org/jacoco/trunk/doc/changes.html
