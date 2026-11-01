# OpenJControlTower — quickstart

## What is included?

An MVP with:

- Java 25, Spring Boot 4.1.2 and Spring AI 2.0.2 (optional)
- React 19 and TypeScript
- Keycloak
- PostgreSQL, with Flyway migrations
- an OpenAPI contract
- an adapter for the OpenJCockpit spec queue
- Kubernetes manifests for a first installation

## Run locally

```bash
cp .env.example .env
docker compose up --build
```

Go to `http://localhost:4000` and sign in with the development user `developer`, password `DevOnly-ChangeMe123!`. This account is for local development only.

Connect the repository under **Repositories**. The stack includes a development Git server with one repository:

- Git repository URL: `http://git-server:8080/git/example.git`
- Authentication: *Username and password*, `developer` / `DevOnly-git-123`
- an OpenJCockpit project ID and workflow ID (placeholders are fine while dispatch is disabled)

For a real repository, use its HTTPS URL and an access token with write access. The token is stored encrypted and is never shown again; leave the field empty when editing to keep it.

**Check connection** shows whether the URL answers as a Git repository and whether the credentials are accepted, before you save. It sends one HTTP request (`<url>/info/refs?service=git-upload-pack`) from the backend; nothing is cloned. A stored token is only reused for the host it was entered for: change the host and you enter the token again. The check confirms read access only, so a token without write access still fails when a spec is pushed.

The frontend has screens for:

- repositories
- interview templates
- spec file templates
- interviews
- spec review
- the dependency graph
- the queue

Each screen has its own URL (for example `/specs` or `/graph`), so you can bookmark it. The selected project is kept in the URL as `?project=<id>`.

Run the **project interview** first. After that, every new feature request needs its own **feature interview**. When an interview is finished, you can generate draft specs and approve them.

Approving a spec starts a disposable, isolated container that clones the repository, creates the branch `spec/<spec id>`, commits each spec file separately and pushes. The container is removed afterwards. To look at a branch of the development repository:

```bash
git clone -b spec/<spec id> http://developer:DevOnly-git-123@localhost:8418/git/example.git
```

## Real AI

By default `AI_ENABLED=false`, and a deterministic demo planner is used instead of a model. To enable Spring AI:

1. Set `AI_ENABLED=true`.
2. Configure `OPENAI_API_KEY`.
3. Restart the backend.

The prompts contain interview answers, so check your privacy and data-processing policies first.

## Speech

The **Speak** button lets the browser dictate answers, and **Read question** reads the question aloud. Both use the browser's language setting, and both depend on browser support and may use an external browser speech service. There is no private, server-side audio engine yet.

## OpenJCockpit integration

Set `COCKPIT_ENABLED=true`, and configure the server URL, the service account credentials and the OAuth2 token endpoint.

The Control Tower only sends specs to OpenJCockpit that the DAG considers ready. OpenJCockpit reads specs from the repository's branch, so first merge the spec branches, for example through a pull request.

The MVP does not yet verify the status of the upstream queue automatically. Confirm a completed spec manually, after checking it in OpenJCockpit.

## Testing

- **Backend:** `mvn clean verify` requires **100% JaCoCo line coverage**, and the build meets it with 115 tests and no exclusions, verified with JDK 25 and Maven 3.9. The report is in `target/site/jacoco/index.html`.
- **Frontend:** build and test it with `npm run build` and `npm run test`.
- **Browser end-to-end tests:** run `sh scripts/run-e2e.sh`. They use Playwright with real Keycloak logins and push real spec branches to a test Git server. Only Docker is required, and the test environment is always removed afterwards.

## Kubernetes

Kustomize manifests for a first installation on a Kubernetes cluster are in `deploy/kubernetes`:

1. Copy the `first-run` overlay.
2. Fill in every value marked `CHANGE ME`: images, Keycloak, host, `config.js` and `secrets.env` (including the key that encrypts Git tokens).
3. Deploy with `kubectl apply -k`.

Notes:

- Keycloak is not included: use an existing realm.
- The backend deliberately runs as a single replica.
- Each approved spec is pushed from its own Kubernetes Job, which is deleted afterwards.

The full procedure is in `docs/KUBERNETES.md`.

Read `README.md`, `docs/KUBERNETES.md`, `docs/ARCHITECTURE.md` and `docs/PRODUCTION.md` for all limitations and configuration options.
