import { expect, test } from '@playwright/test';
import { as, users } from './support';

const policy = { enabled: true, url: 'http://opa:8181', decisionPath: 'openjcontroltower/decision', failOpen: false };

test('an administrator reads, tests and saves the policy agent settings', async ({ request }) => {
  const headers = await as(request, users.admin);

  const view = await (await request.get('/api/settings', { headers })).json();
  expect(view.editable).toBe(true);
  expect(view.policy).toMatchObject({ enabled: true, url: 'http://opa:8181' });

  const status = await request.post('/api/settings/policy/test', { headers, data: policy });
  expect(status.status()).toBe(200);
  const body = await status.json();
  expect(body.reachable).toBe(true);
  expect(body.policies.join(',')).toContain('repository.rego');
  expect(body.policies.join(',')).toContain('spec_approval.rego');
  expect(body.policies.join(',')).toContain('dispatch.rego');

  // An unreachable agent is not saved, and the working setting survives.
  const unreachable = await request.put('/api/settings/policy', { headers, data: { ...policy, url: 'http://opa:9999' } });
  expect(unreachable.status()).toBe(400);
  expect((await (await request.get('/api/settings', { headers })).json()).policy.url).toBe('http://opa:8181');

  const saved = await request.put('/api/settings/policy', { headers, data: policy });
  expect(saved.status()).toBe(200);
});

test('other users see the settings read-only', async ({ request }) => {
  for (const user of [users.editor, users.viewer]) {
    const headers = await as(request, user);
    const view = await (await request.get('/api/settings', { headers })).json();
    expect(view.editable).toBe(false);
    expect((await request.put('/api/settings/policy', { headers, data: policy })).status()).toBe(403);
    expect((await request.post('/api/settings/policy/test', { headers, data: policy })).status()).toBe(403);
  }
});

test('a policy refuses repositories on Git hosts that are not allowed', async ({ request }) => {
  const headers = await as(request, users.editor);
  const denied = 'Denied by policy: Git host github.com is not on the list of allowed Git hosts';
  const url = 'https://github.com/acme/repo.git';

  const check = await request.post('/api/projects/connection-check', { headers, data: { gitUrl: url, credentialType: 'NONE' } });
  expect(check.status()).toBe(403);
  expect((await check.json()).error).toContain(denied);

  const create = await request.post('/api/projects', {
    headers,
    data: { name: 'Not allowed', gitUrl: url, credentialType: 'NONE', cockpitProjectId: 'cockpit', workflowId: 'wf' },
  });
  expect(create.status()).toBe(403);
  expect((await create.json()).error).toContain(denied);
});
