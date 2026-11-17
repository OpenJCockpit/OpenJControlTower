-- Sign-in is the OpenJCockpit realm only; the runtime Keycloak settings are gone.
DELETE FROM settings WHERE name = 'keycloak';
