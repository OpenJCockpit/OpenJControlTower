import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, createKeycloak, loadKeycloakConfig, resolveKeycloakConfig } from './client';

vi.mock('keycloak-js', () => ({
  default: class MockKeycloak {
    token = 'unit-test-jwt';
    updateToken = vi.fn().mockResolvedValue(true);
  },
}));

afterEach(() => vi.unstubAllGlobals());

describe('contract API client', () => {
  beforeEach(() => { createKeycloak({ url: 'http://kc', realm: 'r', clientId: 'c' }); });
  it('includes the current bearer token and reads a successful JSON response', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ name: 'demo' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));
    vi.stubGlobal('fetch', fetchMock);

    await expect(api<{ name: string }>('/projects')).resolves.toEqual({ name: 'demo' });
    expect(fetchMock).toHaveBeenCalledWith('/api/projects', expect.objectContaining({
      method: 'GET',
      headers: expect.objectContaining({ Authorization: 'Bearer unit-test-jwt' }),
    }));
  });

  it('serializes POST payloads and forwards the HTTP method', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'p1' }), {
      status: 201,
    }));
    vi.stubGlobal('fetch', fetchMock);

    await api('/projects', 'POST', { name: 'Demo' });
    expect(fetchMock).toHaveBeenCalledWith('/api/projects', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ name: 'Demo' }),
    }));
  });

  it('propagates API error messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: 'No access' }), {
      status: 403,
    })));
    await expect(api('/projects')).rejects.toThrow('No access');
  });
});

describe('Keycloak configuration', () => {
  it('prefers runtime config, then build-time env, then local defaults', () => {
    expect(resolveKeycloakConfig({ keycloakUrl: 'https://id.example.org' }, { VITE_KEYCLOAK_URL: 'https://build', VITE_KEYCLOAK_REALM: 'eng' }))
      .toEqual({ url: 'https://id.example.org', realm: 'eng', clientId: 'controltower-web' });
    expect(resolveKeycloakConfig()).toEqual({ url: 'http://localhost:8081', realm: 'openjcontroltower', clientId: 'controltower-web' });
  });
});

describe('Keycloak settings from the backend', () => {
  const fallback = { url: 'http://fallback', realm: 'f', clientId: 'fc' };
  it('uses the settings the backend publishes before sign-in', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ url: 'https://id.example.org', realm: 'eng', clientId: 'web' })));
    vi.stubGlobal('fetch', fetchMock);
    await expect(loadKeycloakConfig(fallback)).resolves.toEqual({ url: 'https://id.example.org', realm: 'eng', clientId: 'web' });
    expect(fetchMock).toHaveBeenCalledWith('/api/public/auth-config');
  });
  it('falls back when the backend fails, answers incompletely or cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 503 })));
    await expect(loadKeycloakConfig(fallback)).resolves.toEqual(fallback);
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ url: 'https://id.example.org' }))));
    await expect(loadKeycloakConfig(fallback)).resolves.toEqual(fallback);
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(loadKeycloakConfig(fallback)).resolves.toEqual(fallback);
  });
});
