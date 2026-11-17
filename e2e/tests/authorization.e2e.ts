import { expect, test } from '@playwright/test';
import { as, users } from './support';

const repository = {
  name: 'Viewer attempt',
  gitUrl: 'https://git.example.org/team/repo.git',
  credentialType: 'NONE',
  cockpitProjectId: 'cockpit',
  workflowId: 'wf',
};

test('Keycloak rejects invalid credentials', async ({ request }) => {
  const response = await request.post('http://keycloak:8080/realms/openjcontroltower/protocol/openid-connect/token', {
    form: { grant_type: 'password', client_id: 'controltower-web', username: users.editor.username, password: 'wrong-password' },
  });
  expect(response.status()).toBe(401);
});

test('the API rejects requests without a bearer token', async ({ request }) => {
  expect((await request.get('/api/projects')).status()).toBe(401);
  expect((await request.post('/api/projects', { data: {} })).status()).toBe(401);
});

test('the API rejects a malformed bearer token', async ({ request }) => {
  const response = await request.get('/api/projects', { headers: { Authorization: 'Bearer not-a-jwt' } });
  expect(response.status()).toBe(401);
});

test('health probes are public', async ({ request }) => {
  expect((await request.get('/actuator/health/readiness')).status()).toBe(200);
});

test('a viewer can read but not register repositories', async ({ request }) => {
  const headers = await as(request, users.viewer);
  const list = await request.get('/api/projects', { headers });
  expect(list.status()).toBe(200);
  expect(await list.json()).toEqual([]);
  const create = await request.post('/api/projects', { headers, data: repository });
  expect(create.status()).toBe(403);
  expect(await (await request.get('/api/projects', { headers })).json()).toEqual([]);
});
