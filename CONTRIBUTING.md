# Contributing to OpenJControlTower

Thanks for helping build an auditable, human-directed software factory.

## Working agreements

1. Open an issue describing the problem, constraints and an independently testable outcome. For significant changes include a spec under `specs/NNN-slug/` with explicit predecessor IDs.
2. Keep contributions narrow; separate cleanup from behavior changes.
3. Respect the dependency direction: `domain` has no framework imports; `application` uses ports; `infrastructure` implements integrations; `api` is HTTP-only.
4. Update `contracts/openapi.yaml` before changing public behavior, regenerate the frontend types and document compatibility implications.
5. Add positive, negative, validation and security tests. `mvn clean verify` requires **100% JaCoCo line coverage** and currently passes; keep it that way without exclusions. Prefer real collaborators where they are cheap (H2 + Flyway for `DataStore`, the local `StubHttpServer` for HTTP adapters, `ControlTowerIntegrationTest` for REST/security). Include frontend tests, and extend the Playwright suite in `e2e/tests` for new interactive paths. Use accessible names (labels, roles) as selectors.
6. Schema changes go in a **new** Flyway migration (`src/main/resources/db/migration/V<n>__description.sql`). Never edit an applied migration.
7. Kubernetes changes: keep `kubectl kustomize deploy/kubernetes/overlays/first-run` rendering (after copying the `*.example` files) and the `restricted` Pod Security Standard satisfied.
8. Never commit credentials, production data, raw interview audio or identifiable user data. Use `.env.example` for placeholders.
9. Document behavior and risks in README/architecture/production docs. Demonstrate in the PR how specs and DAG constraints are preserved.
10. Sign off when relevant to the project policy, and ensure third-party dependency licenses are compatible with Apache-2.0.

## Checks

```bash
mvn clean verify
cd frontend
npm install
npm run generate:api
npm run build
npm run test
cd ..
sh scripts/run-e2e.sh   # browser E2E, needs Docker
```

Use a Java 25 JDK, Maven 3.9+ and Node 22+. A failing JaCoCo gate must not be waved through by excluding untested production code.

## Issues and pull requests

Prefer an issue before a large refactor. PRs should include: problem statement, design alternatives, acceptance criteria, screenshots for UI changes, API diff, tests executed and any residual risk. No external service credentials in logs or screenshots.

Be respectful and constructive. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Dependency update pull requests

Renovate is the dependency update bot. Review release notes, Java 25/Spring Boot 4.1 compatibility, security advisories, API contract changes, licensing, and the complete GitHub Actions checks before merging update PRs. Never bypass the 100% JaCoCo line-coverage gate. Major and security updates require explicit maintainer approval; automatic merging is disabled. See [docs/RENOVATE.md](docs/RENOVATE.md).
