<p align="center">
  <img src="docs/img/OpenJControlTower_logo.png" alt="OpenJControlTower logo" width="560">
</p>

<h1 align="center">OpenJControlTower</h1>

<p align="center"><strong>From ambitious ideas to small, dependency-aware implementation specifications.</strong></p>

[![License: Apache 2.0](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
![Java 25](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-green)
![React](https://img.shields.io/badge/React-19-61dafb)
![Coverage](https://img.shields.io/badge/line%20coverage-100%25-brightgreen)
![Kubernetes](https://img.shields.io/badge/Kubernetes-first--run%20ready-326ce5)

OpenJControlTower is an open-source **interview-first control plane** for specification-driven software development. It interviews people about a project or feature, optionally uses **Spring AI** to ask clarifying questions and split requirements into **small, testable specs**, validates their **DAG dependencies** and prepares safe execution in [OpenJCockpit](https://github.com/OpenJCockpit/OpenJCockpit).

> **Project status:** Project that is ready for a **first run on a Kubernetes cluster** for a trusted team (see [Kubernetes deployment](docs/KUBERNETES.md)). `mvn clean verify` passes with **100% JaCoCo line coverage** (115 tests, no exclusions) on JDK 25. It is **strongly advised when used for open/internet-wide production to look at**: per-project RBAC, audit logging and upstream queue reconciliation. Demo mode does not call an LLM. The OpenJCockpit integration must be verified against the installed upstream OpenAPI version, identity roles and repository availability.

## ✨ Capabilities

- **Repository onboarding:** register a remote Git repository by HTTPS URL, base branch and credentials (none, access token, or username and password), like OpenJCockpit's project configuration. Tokens are encrypted at rest and never returned by the API. **Check connection** tests the URL and credentials with a single Git smart-HTTP request (`/info/refs`), without the git CLI and without cloning. Associate an OpenJCockpit project ID and workflow ID.
- **Isolated Git harness:** approving a spec runs a disposable, locked-down container (Docker locally, a Kubernetes Job on a cluster) that clones the repository, creates the branch `spec/<spec id>`, commits every file separately and pushes. The container is then removed. Nothing is written to the backend's filesystem.
- **Editable interview and spec templates:** separate project/feature question sets and reusable Markdown file formats with named placeholders; bounded AI follow-up limits.
- **Conversational discovery:** persistent sessions and follow-ups, whether typed or dictated using browser speech recognition; optional spoken questions.
- **Small spec decomposition:** optional Spring AI structured output; strict cap of six specs per generation batch, immutable unique IDs, explicit prerequisites and human review before anything is pushed.
- **Repo-native files:** `specs/NNN-slug/spec.md`, `plan.md`, `tasks.md`, `review.md` and the spec index `specs/_index.md`.
- **Dependency DAG:** Kahn-based topological ordering, missing-edge, duplicate-edge and cycle validation. Only approved specs whose dependencies are DONE are ready.
- **Decision adapters:** deterministic fallback, or opt-in Jev/Laya `/v1/systemone` ranking for ready nodes.
- **OpenJCockpit dispatch:** service-to-service OAuth2 client credentials; queue operation using `specFile` and `workflowId`, failure-conservative deduplication.
- **Keycloak:** OAuth2/OIDC JWT resource server, browser Authorization Code + PKCE, configurable issuer and roles.
- **Contract-first development:** `contracts/openapi.yaml` plus generated frontend TypeScript types, with Spring `/v3/api-docs` for runtime inspection.
- **Kubernetes-ready operations:** Flyway-versioned schema, liveness/readiness probes, Prometheus metrics, structured JSON logs, graceful shutdown, bounded outbound HTTP timeouts, non-root read-only containers and Kustomize manifests that pass the `restricted` Pod Security Standard.

## 🧭 Workflow

```mermaid
flowchart LR
  R[Git repository] --> T[Interview templates]
  T --> I[Project discovery]
  I --> F[Feature discovery]
  F --> S[Small spec drafts]
  S --> A[Human approval]
  A --> G[spec branch in Git]
  G --> D[Dependency DAG]
  D --> Q[Ready work ranking]
  Q --> O[OpenJCockpit queue]
  O --> W[Implementation and review]
```

## 🚀 Five-minute local evaluation

A condensed walkthrough is also available in the [quickstart](docs/QUICKSTART.md).

### Prerequisites
- Docker Engine and `docker compose` plugin
- Git
- Ports **4000**, **8080**, **8081**, **8418** free

```bash
cp .env.example .env
docker compose up --build
```

The stack includes a development Git server with one repository, so nothing external is needed. The backend reaches Docker only through a socket proxy that allows container and image calls.

Open **http://localhost:4000** and sign in to the *local development-only* Keycloak realm using `developer` / `DevOnly-ChangeMe123!`. Change/delete this account before any shared deployment.

1. Open **Repositories** and connect the development repository:
   - Git repository URL `http://git-server:8080/git/example.git` (plain HTTP is allowed only because `.env` sets `GIT_ALLOW_HTTP=true`)
   - Authentication *Username and password*: `developer` / `DevOnly-git-123`
   - your OpenJCockpit project ID and workflow ID; placeholders are fine while dispatch is disabled.

   For a real repository, use its HTTPS URL and an access token with write access.
2. Choose **Discovery interviews**. Two interview question templates and one OpenJCockpit-compatible Markdown spec template are seeded automatically; additional templates can be created under **Interview templates**.
3. Finish an interview. For each question, type or use **Speak** if your browser supports speech recognition.
4. Generate spec drafts, inspect the Markdown, approve individual specs. Each approval pushes a branch `spec/<spec id>` with one commit per file. Look at it from your machine:
   `git clone -b spec/<spec id> http://developer:DevOnly-git-123@localhost:8418/git/example.git`
5. Review the dependency graph. Merge the spec branches into the branch OpenJCockpit reads before you attempt dispatch.
6. Set `COCKPIT_ENABLED=true` and configure the upstream URL, workflow identity and service credentials to actually send ready specs to OpenJCockpit.

**AI is disabled by default.** The deterministic demo assistant creates illustrative specs; do not interpret these as vetted requirements. Real AI:

```dotenv
AI_ENABLED=true
OPENAI_API_KEY=your-managed-secret
AI_MODEL=gpt-4o-mini
```

Recreate the backend container after updating `.env`:

```bash
docker compose up -d --build backend
```

### Native developer workflow

Use JDK 25, Maven 3.9+ and Node 22+:

```bash
docker compose up -d keycloak git-server docker-proxy
export GIT_CREDENTIALS_SECRET=dev-only-change-me-at-least-32-characters GIT_CREDENTIALS_SALT=5c0744940b5c369b
mvn spring-boot:run
cd frontend
npm install
npm run generate:api
npm run dev
```

Vite runs at **http://localhost:5173**. A backend running on your machine cannot resolve the Compose service names: to approve specs, expose the socket proxy (`DOCKER_PROXY_URL`) and use a Git URL that is reachable from the job containers, or run the backend in Compose.

## ☸️ Kubernetes

Kustomize manifests live in [`deploy/kubernetes`](deploy/kubernetes): a `base`, optional components (`bundled-postgres`, `network-policies`) and a `first-run` overlay with every value to change marked `CHANGE ME`.

```bash
cd deploy/kubernetes/overlays/first-run
cp secrets.env.example secrets.env   # DB password, Git credential encryption key, Cockpit client secret, …
# edit kustomization.yaml (images, Keycloak, host) and config.js (browser OIDC settings)
kubectl apply -k .
kubectl -n openjcontroltower rollout status deploy/openjcontroltower-backend
```

What you get: one stateless backend replica (`Recreate` strategy) that may create a Kubernetes Job per approved spec, two frontend replicas behind a PodDisruptionBudget, an Ingress, and optional single-instance PostgreSQL for evaluation. Keycloak is **not** deployed: point the overlay at an existing realm. Images are published to GHCR by CI on `main` and `v*` tags.

Read the full [Kubernetes deployment guide](docs/KUBERNETES.md) for prerequisites, Keycloak client setup, spec branches and Git credentials, upgrades, backups and troubleshooting.

## ✅ GitHub Actions CI

The repository contains [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Push or open a pull request to run these jobs:

| Job | What it checks |
|---|---|
| `backend` | Temurin Java 25, Maven `clean verify`, tests, **strict 100% JaCoCo line-coverage gate**, test & coverage artifacts |
| `frontend` | Node 22, install dependencies, regenerate TypeScript types from OpenAPI, TypeScript/Vite build, Vitest |
| `containers` | Both backend and frontend Docker images build with Docker |
| `renovate-config` | Validates `renovate.json` with the Renovate strict config validator |
| `e2e` | `scripts/run-e2e.sh`: Playwright against a disposable Compose stack with real Keycloak logins; report and traces uploaded as artifacts |
| `kubernetes-manifests` | Renders the `first-run` overlay, with and without the network-policy component |
| `publish-images` | On `main` and `v*` tags, after all checks pass: pushes both images to `ghcr.io/<owner>/openjcontroltower-{backend,frontend}` |

`mvn clean verify`, the frontend build/tests, the 9 browser E2E tests and both Docker images have been verified locally on JDK 25.0.3 / Maven 3.9.16 / Node 22. The workflows themselves have not yet executed on GitHub, because no remote is connected yet.

## 🔄 Dependency updates with Renovate

[Renovate](https://github.com/apps/renovate) manages dependency version update PRs for **Maven (Java/Spring Boot/Spring AI), npm (React/TypeScript), GitHub Actions and Docker images**. See [`renovate.json`](renovate.json) and [Renovate setup and maintenance](docs/RENOVATE.md).

- Weekly regular update window: **Monday, 02:00–06:59 Europe/Amsterdam** (subject to the hosted app's run frequency). Up to five regular update PRs can be open simultaneously.
- Dependency Dashboard summarizes planned upgrades and PRs. Major upgrades and all security fixes require manual review; **no automatic merges**.
- Renovate may use GitHub Dependabot **alerts** and OSV advisories for security fix PRs. GitHub Dependabot **version updates are removed** to avoid duplicate PRs.
- The GitHub Actions `renovate-config` job validates this policy on each push/PR. A config file alone does **not** activate the bot: install the [Mend Renovate GitHub App](https://github.com/apps/renovate) for this repository and enable the Dependency graph and Dependabot alerts.

Spring Boot 4.1 uses **Jackson 3**, so storage and decision classes were also migrated from `com.fasterxml.jackson.*` to `tools.jackson.*`. See [CI and migration notes](docs/CI_JAVA25_MIGRATION.md).

## 📂 Repository layout

```text
openjcontroltower/
├── contracts/openapi.yaml             # single frontend-backend contract
├── src/main/java/org/openjcontroltower/
│   ├── domain/                       # DAG rules, types, stable IDs
│   ├── application/                  # discovery, generation, dispatch
│   ├── infrastructure/               # JDBC, Git jobs (Docker/Kubernetes), Laya/Jev, Cockpit API
│   ├── api/                          # REST adapter
│   └── config/                       # security, seed templates
├── src/test/                          # JVM tests
├── frontend/src/                      # React 19 / TypeScript / SCSS
├── src/main/resources/db/migration/   # Flyway versioned schema migrations
├── infrastructure/keycloak/           # local Keycloak realm (development only)
├── infrastructure/git-server/         # Git server over HTTP (development and E2E only)
├── e2e/                              # Playwright browser tests + disposable Compose stack
├── deploy/kubernetes/                 # Kustomize base, components and first-run overlay
├── docs/                              # architecture, Kubernetes + production guidance
├── specs/                             # implementation roadmap as small specs
├── .github/workflows/ci.yml
├── renovate.json                     # Renovate dependency update policy
├── .java-version
├── Dockerfile / docker-compose.yml
├── docker-compose.production.yml
└── LICENSE / CONTRIBUTING.md / CODE_OF_CONDUCT.md
```

## 🔌 Contracts and integrations

- **Control Tower contract:** [`contracts/openapi.yaml`](contracts/openapi.yaml). The frontend imports `components['schemas']` generated by `openapi-typescript`.
- **Regenerate client types:** `cd frontend && npm run generate:api`.
- **Runtime contract:** `http://localhost:8080/v3/api-docs` (Swagger UI at `/swagger-ui.html`). Consider API-diff tests before changing an endpoint.
- **OpenJCockpit:** `POST /api/projects/{projectId}/spec-queue/items` with `{ "specFile": "001-feature/spec.md", "workflowId": "..." }`. This is the intended upstream contract as inspected in project docs; validate against the exact version deployed. The file path is relative to `specs/`.
- **Jev/Laya:** configurable `DECISION_PROVIDER` (`deterministic`, `laya`, `jev`), `DECISION_ENDPOINT`, `DECISION_API_KEY`. Ranking cannot bypass the DAG.

### Authentication configuration

| Variable | Purpose |
|---|---|
| `KEYCLOAK_ISSUER_URI` | Exact external JWT `iss`, can point to OpenJCockpit's realm |
| `KEYCLOAK_JWKS_URI` | Network-reachable JWKS URL for backend validation |
| `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_REALM`, `VITE_KEYCLOAK_CLIENT_ID` | Browser OIDC defaults at frontend build time; overridden at runtime by `/config.js` (`window.__OJCT_CONFIG__`), which Kubernetes mounts from a ConfigMap |
| `CONTROLTOWER_EDITOR_ROLE`, `CONTROLTOWER_ADMIN_ROLE` | Configurable write-authorized realm roles |
| `COCKPIT_TOKEN_URI`, `COCKPIT_CLIENT_ID`, `COCKPIT_CLIENT_SECRET` | Separate service-account authorization for queue dispatch |
| `COCKPIT_BASE_URL`, `COCKPIT_ENABLED` | OpenJCockpit endpoint / opt-in dispatch switch |
| `GIT_CREDENTIALS_SECRET`, `GIT_CREDENTIALS_SALT` | **Required.** Key (32+ characters) and hex salt that encrypt the Git tokens stored per repository |
| `GIT_RUNNER` | `docker` (through `DOCKER_PROXY_URL`, joining `GIT_JOB_DOCKER_NETWORK`) or `kubernetes` (a Job per spec) |
| `GIT_JOB_IMAGE`, `GIT_JOB_TIMEOUT` | Git job image (default `alpine/git:2.49.1`) and its time limit (default `90s`) |
| `GIT_BRANCH_PREFIX`, `GIT_AUTHOR_NAME`, `GIT_AUTHOR_EMAIL` | Spec branch prefix (default `spec`) and commit author |
| `GIT_ALLOW_HTTP` | Accept `http://` Git URLs; only for the development Git server |
| `AI_ENABLED`, `OPENAI_API_KEY`, `AI_MODEL` | Enable Spring AI-based reasoning instead of demo assistant |
| `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE` | JDBC connection; the schema is migrated by Flyway on startup |
| `HTTP_CONNECT_TIMEOUT`, `HTTP_READ_TIMEOUT` | Bounds for OpenJCockpit and decision-provider calls (default `5s` / `30s`) |
| `API_DOCS_ENABLED` | Serve `/v3/api-docs` and Swagger UI (default `true`; `false` in Kubernetes) |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | Set to `ecs` (Kubernetes default) or `logstash` for JSON logs |

A shared Keycloak realm does **not** imply that OpenJCockpit grants the control tower's service client access; configure a trusted role/audience mapping there.

## 🧪 Quality and verification

```bash
mvn test          # fast feedback
mvn clean verify  # JaCoCo report and STRICT 100% line coverage gate
cd frontend && npm run build
```

`mvn clean verify` currently passes the gate at **100% line coverage, without exclusions**. The report is written to `target/site/jacoco/index.html`. The suite contains:

- **Unit tests** for the domain, all application services and the Spring AI adapter.
- **Infrastructure tests:**
  - `DataStore` against H2, migrated by Flyway.
  - The OpenJCockpit and decision-provider HTTP adapters against a real local HTTP server, including errors and timeouts.
  - The Git publisher (commits, credentials, masking of secrets in errors) and the Docker and Kubernetes job runners against a simulated Docker Engine and Kubernetes API.
- **A full `@SpringBootTest`** that drives the REST API through Spring Security with JWTs: it covers authorization, validation, and the interview → spec → approval → dispatch flow.

**Browser end-to-end tests** (Playwright, [`e2e/`](e2e)) run the real stack: PostgreSQL, Keycloak with a test-only realm, a Git server, a Docker socket proxy, the backend and the nginx frontend. Chromium signs in through the actual Keycloak login form, and the suite covers:

- **Authorization:** login redirect, rejected credentials, `401` without a token, a viewer receiving `403` on writes, and sign-out.
- **The main flow:** register a repository with credentials (and reject a URL with embedded credentials), check the connection with a wrong and a right password, check that the password stays write-only when editing, run a project interview with an adaptive follow-up, generate three dependent specs, approve the root spec (then clone the pushed branch and check its files and its five commits), check DAG readiness, dispatch with the `UNKNOWN` no-retry outcome, and start a feature interview. The script fails if any Git job container is left behind.
- **Navigation:** every sidebar section has its own URL, the back button works, and a bookmarked URL survives the Keycloak login.

```bash
sh scripts/run-e2e.sh   # needs only Docker; builds, runs, and always tears the stack down
```

The HTML report and failure traces are written to `e2e/playwright-report/` and `e2e/test-results/`.

100% line coverage and green E2E tests are a floor, not proof of correctness, so keep adding negative-path tests with every change. ISO/IEC 25010 is a design target; a formal compliance assessment has not been performed. See [Architecture](docs/ARCHITECTURE.md).

## 🔐 Security and privacy

JWT validation and server-side roles are enabled. Git tokens are encrypted at rest (AES-256-GCM), write-only through the API, and only passed to the isolated job container; errors are masked. Git work runs in a disposable container with a read-only root filesystem, no capabilities and memory, process and time limits. OpenJCockpit HTTP outcomes are treated conservatively. Interview text and specifications may contain intellectual property: configure LLM and voice services according to your own data policy. In-browser speech recognition may send audio to a browser vendor. Do not send credentials, secrets or sensitive personal data in interview prompts.

Read [SECURITY.md](SECURITY.md), the [Kubernetes guide](docs/KUBERNETES.md) and [Production guidance](docs/PRODUCTION.md) before exposing this service. Containers run as non-root with read-only root filesystems and all capabilities dropped; health, metrics and versioned migrations are in place. Still missing for wider exposure: project-level permissions (every editor can write to every registered project, with its stored token), an audit trail, and automated upstream queue reconciliation. In Docker, the backend can create containers through the socket proxy, which is close to root on that host: run it on a dedicated host or use Kubernetes.

## 🛣️ Roadmap

Track the next increments in [`specs/_index.md`](specs/_index.md), including verification that spec branches are merged before dispatch, verified Cockpit status reconciliation, full-duplex private speech adapters and workspace RBAC/auditing. Versioned migrations (CT-0006), and 100% line coverage with browser E2E tests (CT-0007), are done.

## 🤝 Contributing

Issues and thoughtful pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md), our [Code of Conduct](CODE_OF_CONDUCT.md) and [security reporting guide](SECURITY.md). New features start with a small spec, clear acceptance criteria and tests.

## 📜 License

Copyright 2026 OpenJControlTower contributors. Licensed under [Apache License 2.0](LICENSE). OpenJCockpit is a separate upstream project; this scaffold uses its publicly documented API as an integration boundary, not copied source code.
