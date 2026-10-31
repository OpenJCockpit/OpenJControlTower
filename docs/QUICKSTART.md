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
sh scripts/init-demo-repo.sh
docker compose up --build
```

Go to `http://localhost:4000` and sign in with the development user `developer`, password `DevOnly-ChangeMe123!`. This account is for local development only.

Register the repository in the interface. With Docker, its path is `/workspace/repos/example`. Also enter an OpenJCockpit project ID and workflow ID.

The frontend has screens for:

- repositories
- interview templates
- spec file templates
- interviews
- spec review
- the dependency graph
- the queue

Run the **project interview** first. After that, every new feature request needs its own **feature interview**. When an interview is finished, you can generate draft specs, approve them, and save them to the `specs/` folder of the registered repository.

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

The Control Tower only sends specs to OpenJCockpit that the DAG considers ready. You must first check yourself that the spec files are committed and pushed to a branch that OpenJCockpit can access.

The MVP does not yet verify the status of the upstream queue automatically. Confirm a completed spec manually, after checking it in OpenJCockpit.

## Testing

- **Backend:** `mvn clean verify` requires **100% JaCoCo line coverage**, and the build meets it with 78 tests and no exclusions, verified with JDK 25 and Maven 3.9. The report is in `target/site/jacoco/index.html`.
- **Frontend:** build and test it with `npm run build` and `npm run test`.
- **Browser end-to-end tests:** run `sh scripts/run-e2e.sh`. They use Playwright with real Keycloak logins. Only Docker is required, and the test environment is always removed afterwards.

## Kubernetes

Kustomize manifests for a first installation on a Kubernetes cluster are in `deploy/kubernetes`:

1. Copy the `first-run` overlay.
2. Fill in every value marked `CHANGE ME`: images, Keycloak, host, `config.js`, `secrets.env` and `repositories.txt`.
3. Deploy with `kubectl apply -k`.

Notes:

- Keycloak is not included: use an existing realm.
- The backend deliberately runs as a single replica.
- Commit and push generated specs with `git` from the backend pod.

The full procedure is in `docs/KUBERNETES.md`.

Read `README.md`, `docs/KUBERNETES.md`, `docs/ARCHITECTURE.md` and `docs/PRODUCTION.md` for all limitations and configuration options.
