# Renovate — automated dependency maintenance

OpenJControlTower uses [Renovate](https://docs.renovatebot.com/) for version update pull requests and dependency maintenance. Dependency updates are **proposed**, not silently merged or deployed. The configuration lives at [`renovate.json`](../renovate.json).

## Coverage

Renovate discovers and updates versions in:

- Maven `pom.xml` (Spring Boot parent, Spring AI BOM, springdoc, JaCoCo, Maven plugins, and other explicitly versioned Java libraries).
- `frontend/package.json` (React, TypeScript, Vite, Vitest, Keycloak JS, and other frontend dependencies).
- `.github/workflows/*.yml` (GitHub Actions version references; best-practices preset pins them by SHA).
- Root and frontend `Dockerfile` files and both Docker Compose files (base and service image tags/digests).
- `e2e/package.json` and `e2e/Dockerfile`: `@playwright/test` and the `mcr.microsoft.com/playwright` image are grouped in one PR, because the image bundles the matching browsers.
- The PostgreSQL image in `deploy/kubernetes/components/bundled-postgres`. Application images in overlays are your own release tags, and are not managed by Renovate.

The [`config:best-practices`](https://docs.renovatebot.com/upgrade-best-practices/) preset enables recommended safety measures, including Docker image digest pinning, GitHub Actions SHA pinning, configuration migrations, npm minimum release age, and lockfile maintenance when lockfiles exist. This project does **not currently include an npm lockfile**, so lockfile maintenance becomes applicable only when a lockfile is committed.

## Schedule and review policy

- Regular updates: **Mondays 02:00–06:59** local time (`Europe/Amsterdam`), provided the hosted Renovate service runs during the window. The scheduled window is for **creation of normal update branches/PRs**, not a guarantee of a particular execution time.
- Maximum five concurrent regular update PRs, and two PRs per hour.
- An issue named **Dependency Dashboard** shows available and pending dependency updates.
- **No automerge** (including minor/patch and vulnerability-fix updates). Human review and passing CI are required for all merges; major upgrades receive a `major-update` label.
- React and React DOM updates are grouped together where applicable; compatible non-major GitHub Actions revisions may be grouped.
- If GitHub's Dependency graph and Dependabot alerts are enabled, Renovate can propose security fix PRs for GitHub alerts. OSV-based alerts for supported direct dependencies are enabled via `osvVulnerabilityAlerts` (an experimental Renovate feature). Security PRs are **not constrained to the regular Monday window**.
- 100% JaCoCo line coverage remains a required Maven verification criterion. Do not weaken this check for dependency upgrades.

## GitHub activation (required)

The repository configuration alone does **not** run Renovate. Once the project is pushed to GitHub:

1. Install the [Mend Renovate GitHub App](https://github.com/apps/renovate) and grant it access to the OpenJControlTower repository. Select the repository explicitly if using scoped installation.
2. Ensure `renovate.json` is present on the default branch (it is included in the archive). The hosted app should use this configuration during onboarding.
3. In repository **Settings → Advanced Security**, enable **Dependency graph** and **Dependabot alerts**. These are vulnerability signals, **not** Dependabot's automated version-update PRs. Confirm the Renovate app has Dependabot alerts read permissions if security alert integration is required.
4. Keep **Dependabot version updates disabled**. `.github/dependabot.yml` was removed intentionally, to prevent two bots from proposing identical version changes.
5. Enable GitHub Actions and apply branch protection/rulesets for backend, frontend, containers, and `renovate-config` checks **after the build is actually passing**. Never bypass the gate to get dependency updates merged.
6. Check the Dependency Dashboard issue and review PRs as they are proposed. Security updates should be reviewed promptly.

## Configuration checks

The GitHub Actions `renovate-config` job runs:

```sh
npx --yes --package renovate -- renovate-config-validator --strict
```

You can run the same command locally from the repository root with Node/npm installed. The validator needs network access for the Renovate npm package and any preset resolution. The config can also be checked for JSON syntax with `python -m json.tool renovate.json`.

The bot itself is **not** executed from the GitHub Actions workflow: the installed Renovate GitHub App is responsible for periodically checking registries and proposing update PRs. No personal access token or extra bot secrets are required in this repository's CI configuration.

## What Renovate does not guarantee

- Renovate is not a replacement for dependency vulnerability scanning, SCA policy enforcement, or manual review of breaking changes.
- An available new version may be incompatible with Java 25 / Spring Boot 4.1; CI and maintainers decide whether it can be merged.
- The backend gate (100% line coverage) passes on the current baseline, so a failing update PR points at the update itself. Renovate cannot make an untested change production-ready.
- Until the GitHub App is actually installed and authorized, **no scheduled updates or security PRs will be created**.

References: [Renovate onboarding](https://docs.renovatebot.com/getting-started/installing-onboarding/), [configuration validation](https://docs.renovatebot.com/config-validation/), [vulnerability alerts](https://docs.renovatebot.com/configuration-options/#vulnerabilityalerts), [upgrade best practices](https://docs.renovatebot.com/upgrade-best-practices/).
