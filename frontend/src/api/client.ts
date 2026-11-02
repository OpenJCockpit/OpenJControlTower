import Keycloak from 'keycloak-js';
export type KeycloakConfig={url:string;realm:string;clientId:string};
/** Runtime config (public/config.js, replaced per deployment) wins over build-time VITE_* values. */
export function resolveKeycloakConfig(runtime: RuntimeConfig = {}, env: Partial<ImportMetaEnv> = {}):KeycloakConfig {
  return {
    url: runtime.keycloakUrl || env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
    realm: runtime.keycloakRealm || env.VITE_KEYCLOAK_REALM || 'openjcontroltower',
    clientId: runtime.keycloakClientId || env.VITE_KEYCLOAK_CLIENT_ID || 'controltower-web',
  };
}
/**
 * The Keycloak instance from the settings page, read from the backend before sign-in. When the backend cannot
 * answer, config.js and the build-time values are used instead.
 */
export async function loadKeycloakConfig(fallback:KeycloakConfig=resolveKeycloakConfig(globalThis.window?.__OJCT_CONFIG__, import.meta.env)):Promise<KeycloakConfig> {
  try {
    const response=await fetch('/api/public/auth-config');
    if (!response.ok) return fallback;
    const config=await response.json() as Partial<KeycloakConfig>;
    return config.url&&config.realm&&config.clientId?{url:config.url,realm:config.realm,clientId:config.clientId}:fallback;
  } catch { return fallback; }
}
export let keycloak:Keycloak;
export function createKeycloak(config:KeycloakConfig):Keycloak {
  keycloak=new Keycloak(config);
  return keycloak;
}
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
