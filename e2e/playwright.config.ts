import { defineConfig } from '@playwright/test';

// API tests only: they call the backend directly with tokens issued by the test Keycloak, so no browser is needed.
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
    baseURL: process.env.BASE_URL ?? 'http://backend:8080',
    trace: 'retain-on-failure',
  },
});
