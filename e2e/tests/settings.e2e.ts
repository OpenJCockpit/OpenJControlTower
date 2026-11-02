import { expect, type Page, test } from '@playwright/test';
import { alert, openSection, signIn, users } from './support';

const panel = (page: Page, heading: string) =>
  page.locator('section.panel').filter({ has: page.getByRole('heading', { name: heading, exact: true }) });

test('an administrator configures Keycloak and the policy agent on the settings page', async ({ page }) => {
  await signIn(page, users.admin);
  await page.locator('.sidebar-footer').getByRole('link', { name: 'Settings' }).click();
  await expect(page).toHaveURL(/\/settings$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Settings' })).toBeVisible();

  await test.step('Keycloak: the backend publishes the instance the browser signed in with', async () => {
    const keycloak = panel(page, 'Keycloak');
    await expect(page.getByLabel('Keycloak URL')).toHaveValue('http://keycloak:8080');
    await expect(page.getByLabel('Realm')).toHaveValue('openjcontroltower');
    await expect(page.getByLabel('Client ID')).toHaveValue('controltower-web');
    await keycloak.getByRole('button', { name: 'Test connection' }).click();
    await expect(keycloak.getByRole('status')).toContainText('Connected: the realm publishes');
    await page.getByLabel('Realm').fill('no-such-realm');
    await keycloak.getByRole('button', { name: 'Save Keycloak settings' }).click();
    await expect(keycloak.getByRole('status')).toContainText('Keycloak settings not saved.');
    await page.getByLabel('Realm').fill('openjcontroltower');
    await keycloak.getByRole('button', { name: 'Save Keycloak settings' }).click();
    await expect(keycloak.getByRole('status')).toHaveText('Saved.');
  });

  await test.step('Open Policy Agent: enforcing, with the standard policies loaded', async () => {
    const opa = panel(page, 'Open Policy Agent');
    await expect(opa.getByLabel('Enforce policies')).toBeChecked();
    await expect(page.getByLabel('Agent URL')).toHaveValue('http://opa:8181');
    await opa.getByRole('button', { name: 'Test connection' }).click();
    await expect(opa.getByRole('status')).toContainText('Connected: 5 policies loaded and decision openjcontroltower/decision found.');
    await expect(opa.getByRole('status')).toContainText('dispatch.rego, repository.rego, spec_approval.rego');
    await page.getByLabel('Agent URL').fill('http://opa:9999');
    await opa.getByRole('button', { name: 'Save policy settings' }).click();
    await expect(opa.getByRole('status')).toContainText('Policy settings not saved. Could not reach Open Policy Agent');
    await page.getByLabel('Agent URL').fill('http://opa:8181');
    await opa.getByRole('button', { name: 'Save policy settings' }).click();
    await expect(opa.getByRole('status')).toContainText('Saved. Repository changes, approvals and dispatches are now checked');
  });

  await page.reload();
  await expect(page.getByLabel('Agent URL')).toHaveValue('http://opa:8181');
  await expect(page.getByText('Four-eyes approval')).toBeVisible();
});

test('other users see the settings read-only', async ({ page }) => {
  await signIn(page, users.editor);
  await page.goto('/settings');
  await expect(page.getByText('Only administrators can change these settings.')).toBeVisible();
  await expect(page.getByLabel('Keycloak URL')).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Save Keycloak settings' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Save policy settings' })).toHaveCount(0);
});

test('a policy refuses repositories on Git hosts that are not allowed', async ({ page }) => {
  await signIn(page, users.editor);
  await openSection(page, 'Repositories');
  await page.getByLabel('Project name').fill('Not allowed');
  await page.getByLabel('Git repository URL').fill('https://github.com/acme/repo.git');
  await page.getByLabel('OpenJCockpit project ID').fill('cockpit');
  await page.getByLabel('OpenJCockpit workflow ID').fill('wf');
  await page.getByRole('button', { name: 'Check connection' }).click();
  await expect(page.getByRole('status')).toHaveText('Denied by policy: Git host github.com is not on the list of allowed Git hosts');
  await page.getByRole('button', { name: 'Connect repository' }).click();
  await expect(alert(page)).toContainText('Denied by policy: Git host github.com is not on the list of allowed Git hosts');
});
