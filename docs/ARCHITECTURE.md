# Architecture decision record: control plane, not execution engine

## Context
OpenJCockpit runs implementation workflows; OpenJControlTower elicits requirements and prepares their inputs.

## Decisions
- Java 25 and Spring Boot 4.1.2, with Spring AI 2.0.2 and Jackson 3. The REST boundary remains versioned and independent of OpenJCockpit.
- React 19, Vite, TypeScript and SCSS echo OpenJCockpit frontend conventions without copying its source code or assets.
- OpenAPI 3.0 is the cross-service/frontend contract; generate frontend types with `npm run generate:api`.
- Independent interview-question and Markdown-spec templates are persisted and selected at generation time. The generator substitutes only whitelisted placeholders.
- JDBC + portable H2 (native dev) / PostgreSQL (Docker, Kubernetes, production). The schema is managed by Flyway versioned migrations in `src/main/resources/db/migration`; pre-Flyway databases are adopted as version 1. Backups remain an operator responsibility.
- Domain DAG is pure Java and does not rely on an LLM. Edge A -> B means B needs A completed.
- Spring AI is optional and enabled only with `AI_ENABLED=true`. Fallback mode is demonstrably deterministic, *not AI*.
- An autonomous Embabel agent is deliberately not introduced in the MVP. An explicit application service and Spring AI structured-output boundary are easier to validate and constrain. Add Embabel when multi-agent planning is demonstrably necessary.
- DecisionProvider allows deterministic / Jev / Laya-based ranking only among dependency-ready nodes.
- A project is a remote Git repository (HTTPS URL, base branch, credential type, optional username), modelled on OpenJCockpit's project configuration. The token or password is encrypted with `Encryptors.delux` (AES-256-GCM, PBKDF2-derived key from `GIT_CREDENTIALS_SECRET`/`GIT_CREDENTIALS_SALT`), is write-only through the API (`hasSecret` only), and never appears in a URL, a command line, a file or a log.
- Approval is the only write to a repository. It runs `git-job.sh` in a disposable container: Docker (via a socket proxy that allows only container and image calls) or a Kubernetes Job (with its inputs in a Secret owned by the Job). The container clones the base branch, creates `<GIT_BRANCH_PREFIX>/<spec id>`, commits `specs/NNN-slug/{spec,plan,tasks,review}.md` and the `specs/_index.md` line as separate commits, and pushes. It never force-pushes, refuses an existing branch, and is removed afterwards. Containers run as UID 10001 with a read-only root filesystem, no capabilities, `no-new-privileges`, and memory, process and time limits. Git uses a credential helper that reads the secret from the environment.
- **Check connection** uses Git's smart-HTTP discovery (`GET <url>/info/refs?service=git-upload-pack`) from the backend with the JDK HTTP client: no git CLI, no clone, no redirects followed. A stored secret is only reused for the scheme, host and port it was entered for; the same rule applies when a repository's URL is edited without re-entering the secret.
- The backend holds no repository state, so there is nothing to back up besides the database and the encryption key.
- Dispatch uses a dedicated OAuth2 client-credentials identity, not a user's access token. An UNKNOWN HTTP outcome is non-retriable until reconciled by a human. All outbound HTTP calls have connect/read timeouts.
- Kubernetes deployment uses Kustomize (`deploy/kubernetes`), not Helm: it ships with `kubectl` and keeps manifests reviewable as plain YAML. The backend runs as a **single replica**, because dispatch is serialized in-process. Horizontal scaling needs a distributed lock first.
- Frontend images are environment-neutral: OIDC settings are read at runtime from `/config.js` (mounted from a ConfigMap), with build-time `VITE_*` values as fallback.

## Security boundary
Browser PKCE -> Keycloak -> Bearer token -> Spring resource server. Write routes require a configurable realm role. Use an external or shared issuer by setting `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWKS_URI`, the frontend runtime `config.js` (or `VITE_KEYCLOAK_*`), and service credentials. Health probes and Prometheus metrics are unauthenticated on the backend port, but are not routed through the frontend or Ingress.

## Current MVP limitations
1. Spec branches are pushed, but no pull request is opened, and SSH remotes and GitHub App credentials are not supported (HTTPS with a token or password only).
2. Manual assertion that the spec branch is merged into the branch OpenJCockpit reads. This is not an automated proof of the merge (CT-0001).
3. Manual DONE acknowledgement after verifying the Cockpit run. No upstream queue status reconciliation or webhooks yet.
4. In-browser speech recognition and synthesis: browser-dependent, possibly cloud processed; no backend STT/TTS, real-time duplex streaming, or consent/retention workflows.
5. AI is not used in default demo mode; model responses need production prompt-injection and PII review.
6. External secret storage, rate limits, audit logging, tenant ACLs, and OPA enforcement are future hardening work.
7. Laya/Jev provider may receive spec text; opt in only with data classification and vendor review.
8. Single backend replica: see the deployment decision above.

## Quality / ISO 25010
- Functional suitability: precise, testable acceptance criteria and DAG invariants.
- Performance: one-pass Kahn topological sort; bounded (1-6) spec generation.
- Compatibility: OpenAPI-contract-first integration; isolated REST adapters.
- Reliability: persistent interviews, reserved dispatch IDs, halt-on-uncertain semantics, bounded HTTP timeouts, health probes and graceful shutdown.
- Security: OIDC, realm roles, encrypted write-only Git secrets, isolated disposable Git containers, non-root containers, no untrusted agent tools.
- Maintainability: dependency-free domain classes, adapters at edges, 100% JaCoCo line coverage enforced in the build.
- Usability: interview questions, voice controls, guided review and graph visualization.
- Portability: multi-stage, environment-neutral containers, external configuration and Kustomize manifests.

For any production deployment complete a security threat model, formal risk acceptance and scalability tests.
