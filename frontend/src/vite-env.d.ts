/// <reference types="vite/client" />
interface ImportMetaEnv {
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
  readonly VITE_KEYCLOAK_CLIENT_ID?: string;
}
interface RuntimeConfig {
  keycloakUrl?: string;
  keycloakRealm?: string;
  keycloakClientId?: string;
}
interface Window { __OJCT_CONFIG__?: RuntimeConfig }
