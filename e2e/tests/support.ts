import { expect, type Page } from '@playwright/test';

export const users = {
  editor: { username: 'e2e-editor', password: 'E2E-only-editor-1!' },
  viewer: { username: 'e2e-viewer', password: 'E2E-only-viewer-1!' },
} as const;

/** Opens the app, follows the redirect to Keycloak and signs in through the real login form. */
export async function signIn(page: Page, user: { username: string; password: string }) {
  await page.goto('/');
  await expect(page).toHaveURL(/\/realms\/openjcontroltower\/protocol\/openid-connect\/auth/);
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible();
  await expect(page.locator('.identity')).toContainText(user.username);
}

export async function openSection(page: Page, name: string) {
  await page.locator('nav').getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
}

export const alert = (page: Page) => page.getByRole('alert');
