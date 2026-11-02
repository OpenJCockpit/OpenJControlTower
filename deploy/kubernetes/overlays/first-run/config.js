// Browser-side OIDC settings (public values, not secrets). Fallback only: the web application
// first asks the backend, which serves the Keycloak settings from the settings page.
window.__OJCT_CONFIG__ = {
  keycloakUrl: 'https://identity.example.org', // CHANGE ME
  keycloakRealm: 'engineering', // CHANGE ME
  keycloakClientId: 'openjcontroltower-web', // CHANGE ME
};
