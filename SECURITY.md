# Security Policy

## Supported versions

This repository is an **MVP**. It is suitable for a first Kubernetes deployment for a trusted team ([docs/KUBERNETES.md](docs/KUBERNETES.md)), but no release is certified for internet-facing production use. Security updates target the latest main branch.

## Report a vulnerability

Prefer a private GitHub security advisory (if enabled) or a confidential message to the repository maintainers. Do **not** publish an exploit, credentials or customer data in a public issue. Include affected versions, minimum reproduction and potential impact, without personal data.

We cannot guarantee a particular response-time SLA until maintainers and contacts are published.

## Deployment safety

Never deploy the local sample Keycloak realm, seeded user, demo passwords, the development Git server, `GIT_ALLOW_HTTP=true`, Docker `start-dev` or public HTTP to an untrusted environment. Never publish the Docker socket proxy's port: whoever can create containers through it controls the host. Keep `GIT_CREDENTIALS_SECRET` and `GIT_CREDENTIALS_SALT` in a secret manager; together with a database dump they reveal every stored Git token. Require TLS, proper IAM, per-project authorization, encrypted backups, network isolation, security monitoring and vulnerability scans. Review LLM and browser speech data processing with your privacy office. Queue dispatch must only use a service identity explicitly authorized by OpenJCockpit.

Container images run as numeric non-root users with read-only root filesystems. The Kubernetes manifests satisfy the `restricted` Pod Security Standard and do not mount ServiceAccount tokens. Probe and metrics endpoints are unauthenticated on the backend port and must not be routed publicly; the provided Ingress routes only to the frontend, which proxies `/api` only.

Refer to `docs/PRODUCTION.md` for additional requirements and gaps.
