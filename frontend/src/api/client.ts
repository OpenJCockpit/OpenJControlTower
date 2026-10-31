import Keycloak from 'keycloak-js';
/** Runtime config (public/config.js, replaced per deployment) wins over build-time VITE_* values. */
export function resolveKeycloakConfig(runtime: RuntimeConfig = {}, env: Partial<ImportMetaEnv> = {}) {
  return {
    url: runtime.keycloakUrl || env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
    realm: runtime.keycloakRealm || env.VITE_KEYCLOAK_REALM || 'openjcontroltower',
    clientId: runtime.keycloakClientId || env.VITE_KEYCLOAK_CLIENT_ID || 'controltower-web',
  };
}
export const keycloak = new Keycloak(resolveKeycloakConfig(globalThis.window?.__OJCT_CONFIG__, import.meta.env));
export async function api<T>(path:string, method='GET', body?:unknown):Promise<T> {
  await keycloak.updateToken(30);
  const response=await fetch('/api'+path,{
    method,
    headers:{'Authorization':'Bearer '+keycloak.token,'Content-Type':'application/json'},
    body:body===undefined?undefined:JSON.stringify(body),
  });
  if (!response.ok) {
    const error=await response.json().catch(()=>({error:response.statusText}));
    throw new Error(String(error.error||`HTTP ${response.status}`));
  }
  return response.json() as Promise<T>;
}
