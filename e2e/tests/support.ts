import { expect, type APIRequestContext } from '@playwright/test';

export const users = {
  editor: { username: 'e2e-editor', password: 'E2E-only-editor-1!' },
  viewer: { username: 'e2e-viewer', password: 'E2E-only-viewer-1!' },
  admin: { username: 'e2e-admin', password: 'E2E-only-admin-1!' },
} as const;

const keycloak = process.env.KEYCLOAK_URL ?? 'http://keycloak:8080';
const realm = 'openjcontroltower';
const clientId = 'controltower-web';

type Credentials = { username: string; password: string };
const cache = new Map<string, Promise<string>>();

/** Signs in against the real Keycloak (test-only password grant) and returns the access token. */
export function accessToken(request: APIRequestContext, user: Credentials): Promise<string> {
  let token = cache.get(user.username);
  if (!token) {
    token = (async () => {
      const response = await request.post(`${keycloak}/realms/${realm}/protocol/openid-connect/token`, {
        form: { grant_type: 'password', client_id: clientId, username: user.username, password: user.password },
      });
      expect(response.status(), `token for ${user.username}`).toBe(200);
      return (await response.json()).access_token as string;
    })();
    cache.set(user.username, token);
  }
  return token;
}

/** Authorization header for the given user. */
export async function as(request: APIRequestContext, user: Credentials) {
  return { Authorization: `Bearer ${await accessToken(request, user)}` };
}
