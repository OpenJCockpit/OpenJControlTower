# Production deployment reference

OpenJControlTower is ready for a **first run on Kubernetes for a trusted team**: follow [KUBERNETES.md](KUBERNETES.md). This page lists what is already in place, and what must still be done before opening access more widely. Docker Compose (`docker-compose.production.yml`) remains a single-host reference.

## In place

| Area | What is implemented |
|---|---|
| Schema | Flyway versioned migrations (`src/main/resources/db/migration`), run on startup. Databases created by the old `schema.sql` are adopted as version 1 (`baseline-on-migrate`). |
| Health | `/actuator/health/liveness` and `/actuator/health/readiness` (the readiness check includes the database). Wired to Kubernetes startup, liveness and readiness probes. |
| Observability | Prometheus metrics at `/actuator/prometheus`; structured ECS JSON logs via `LOGGING_STRUCTURED_FORMAT_CONSOLE`. |
| Shutdown | Graceful shutdown (`SHUTDOWN_TIMEOUT`, default 25s), a `preStop` delay, and `terminationGracePeriodSeconds: 40`. |
| Outbound HTTP | Connect/read timeouts (`HTTP_CONNECT_TIMEOUT` 5s, `HTTP_READ_TIMEOUT` 30s) on OpenJCockpit and decision-provider calls, so a hung upstream cannot block dispatch indefinitely. |
| Errors | 5xx responses never include exception messages or stack traces. Validation and conflict errors return explicit messages. |
| Git | Repositories are configured with an HTTPS URL and credentials. Tokens are encrypted at rest (AES-256-GCM) and write-only through the API. Each approval runs in a disposable container or Kubernetes Job (read-only root filesystem, no capabilities, memory/process/time limits) that pushes the branch `spec/<spec id>` with one commit per file, and is then removed. |
| Containers | Numeric non-root UIDs (backend 10001, frontend 101), read-only root filesystem, all capabilities dropped, seccomp `RuntimeDefault`, the `restricted` Pod Security Standard, and no ServiceAccount token mounted. |
| Proxying | `X-Forwarded-*` headers honoured (`FORWARD_HEADERS_STRATEGY`); nginx security headers; `/config.js` served `no-store`. |
| Configuration | Environment-neutral images. Backend settings come from a ConfigMap and Secret; browser OIDC settings from a runtime `config.js`. |
| Quality gate | `mvn clean verify` enforces 100% JaCoCo line coverage. Playwright browser E2E tests run against a real Keycloak. CI renders the Kubernetes manifests and publishes images only after every check passes. |

## Still required before wider exposure

1. **Database:** use a managed PostgreSQL or an operator (for example CloudNativePG) with backups and a tested restore, instead of the `bundled-postgres` component. Grant the application user DDL rights only on its own schema.
2. **Secrets:** deliver `openjcontroltower-backend-secrets` from a secret manager (External Secrets Operator, Sealed Secrets, Vault) instead of a local `secrets.env`. Rotate the database and Cockpit credentials. Back up `GIT_CREDENTIALS_SECRET` and `GIT_CREDENTIALS_SALT`: rotating them is not supported yet, and every stored Git token would have to be entered again.
3. **Git access:** give each repository a token limited to that repository's contents (read and write), and protect the base branch so that spec branches can only reach it through review. Any editor can approve specs for any registered repository with its stored token until per-project ACLs exist (CT-0003). A stored token is never reused for another host, but **Check connection** lets editors make the backend send one GET request to any HTTPS host it can reach; only a classified outcome is returned. Restrict the backend's egress if that matters in your network. With the Docker runner, the backend can start containers through the socket proxy, which amounts to root on that host: use a dedicated host, or Kubernetes.
4. **Identity:** use a dedicated Keycloak realm or clients with exact HTTPS redirect URIs, and a **separate confidential service client** that OpenJCockpit explicitly authorizes for its queue API. Never reuse a browser token. Do not deploy `infrastructure/keycloak/dev-realm.json` or its seeded user.
5. **Network:** enable the `network-policies` component (it requires a CNI that enforces policies), restrict egress where your CNI allows it, and keep the database and Keycloak admin endpoints private.
6. **Ingress hardening:** TLS everywhere, request size limits and rate limiting at the Ingress, and a domain-specific Content-Security-Policy. That CSP must allow your Keycloak origin in `connect-src` and `frame-src`.
7. **Authorization:** every user with the editor role can currently write to every registered project. Per-project ACLs (CT-0003) and an audit trail (CT-0004) are needed before multiple teams share an instance.
8. **AI and decision providers:** set `AI_ENABLED=true` or a Laya/Jev provider only after a review of data residency, classification, retention, prompt injection and cost controls. Interview text and spec bodies leave the cluster.
9. **Dispatch:** verify that OpenJCockpit can read the pushed branch and that workflow IDs match. A 2xx enqueue response does not mean the implementation is complete. `UNKNOWN` outcomes need manual reconciliation (CT-0002).
10. **Supply chain:** pin images by digest, scan the images (for example with Trivy or Grype), and add SAST/DAST to CI. The browser E2E suite already runs in CI.
11. **Capacity:** the backend runs as a single replica by design (in-process dispatch lock). Size its memory from observed usage. Plan for the short downtime of a `Recreate` rollout.

If you share a Keycloak instance with OpenJCockpit, use its issuer and realm-role conventions, and make sure Cockpit explicitly accepts the service client's audience and roles. Sharing Keycloak alone does not grant queue access.
