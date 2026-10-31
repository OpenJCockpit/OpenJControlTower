import { defineConfig, devices } from '@playwright/test';

// keycloak-js needs Web Crypto (PKCE), which browsers only expose in secure contexts.
// The Compose hostnames are plain HTTP, so Chromium is told to treat exactly these origins as secure.
const secureOrigins = process.env.E2E_SECURE_ORIGINS ?? 'http://frontend:8080,http://keycloak:8080';

export default defineConfig({
  testDir: './tests',
  testMatch: '**/*.e2e.ts',
  // One shared backend and database: run sequentially for deterministic state.
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://frontend:8080',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: { args: [`--unsafely-treat-insecure-origin-as-secure=${secureOrigins}`] },
  },
  // The full Chromium build (not the headless shell) honours the secure-origin flag above.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], channel: 'chromium' } }],
});
