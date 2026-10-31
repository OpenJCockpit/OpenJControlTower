import { expect, test } from '@playwright/test';
import { alert, openSection, signIn, users } from './support';

test('anonymous visitors are sent to the Keycloak login page', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/realms\/openjcontroltower\/protocol\/openid-connect\/auth/);
  await expect(page.locator('#kc-login')).toBeVisible();
});

test('invalid credentials are rejected by Keycloak', async ({ page }) => {
  await page.goto('/');
  await page.locator('#username').fill(users.editor.username);
  await page.locator('#password').fill('wrong-password');
  await page.locator('#kc-login').click();
  await expect(page.getByText(/invalid username or password/i)).toBeVisible();
  await expect(page).not.toHaveURL(/frontend:8080\/?$/);
});

test('the API rejects requests without a bearer token', async ({ request }) => {
  expect((await request.get('/api/projects')).status()).toBe(401);
  expect((await request.post('/api/projects', { data: {} })).status()).toBe(401);
});

test('a viewer can read but not register repositories', async ({ page }) => {
  await signIn(page, users.viewer);
  await openSection(page, 'Repositories');
  await expect(page.getByText('No repositories connected.')).toBeVisible();
  await page.getByLabel('Project name').fill('Viewer attempt');
  await page.getByLabel('Absolute repository path').fill('/workspace/repos/e2e-repo');
  await page.getByLabel('OpenJCockpit project ID').fill('cockpit');
  await page.getByLabel('OpenJCockpit workflow ID').fill('wf');
  await page.getByRole('button', { name: 'Connect repository' }).click();
  await expect(alert(page)).toContainText(/403|Forbidden/);
  await expect(page.getByText('No repositories connected.')).toBeVisible();
});

test('signing out ends the session', async ({ page }) => {
  await signIn(page, users.viewer);
  await page.getByRole('button', { name: 'Sign out' }).click();
  // Depending on the Keycloak version the user may have to confirm the logout.
  const confirm = page.locator('#kc-logout');
  if (await confirm.isVisible({ timeout: 3_000 }).catch(() => false)) await confirm.click();
  await page.goto('/');
  await expect(page.locator('#kc-login')).toBeVisible();
});
