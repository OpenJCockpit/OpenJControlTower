import { expect, test } from '@playwright/test';
import { openSection, signIn, users } from './support';

test('every sidebar section has its own bookmarkable URL', async ({ page }) => {
  await signIn(page, users.viewer);
  await openSection(page, 'Dependency graph');
  await expect(page).toHaveURL(/\/graph$/);
  await openSection(page, 'Dispatch queue');
  await expect(page).toHaveURL(/\/queue$/);

  await page.goBack();
  await expect(page.getByRole('heading', { level: 1, name: 'Dependency graph' })).toBeVisible();

  await page.goto('/specs');
  await expect(page.getByRole('heading', { level: 1, name: 'Specifications' })).toBeVisible();
  await expect(page.locator('nav').getByRole('link', { name: 'Specifications' })).toHaveAttribute('aria-current', 'page');
});

test('a bookmarked URL survives the Keycloak login', async ({ page }) => {
  await page.goto('/templates');
  await page.locator('#username').fill(users.viewer.username);
  await page.locator('#password').fill(users.viewer.password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Interview templates' })).toBeVisible();
  await expect(page).toHaveURL(/\/templates$/);
});
