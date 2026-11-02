package org.openjcontroltower.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Instance settings that administrators change at runtime: the Keycloak instance and the Open Policy Agent. */
public final class Settings {
    private Settings() {}
    private static final Pattern REALM=Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern CLIENT_ID=Pattern.compile("[A-Za-z0-9._:-]{1,100}");
    private static final Pattern DECISION_PATH=Pattern.compile("[A-Za-z0-9_]{1,60}(/[A-Za-z0-9_]{1,60}){0,9}");

    /**
     * @param url      Keycloak base URL as the browser reaches it; with the realm it forms the token issuer
     * @param clientId public client the web application signs in with
     * @param backendUrl optional: the Keycloak base URL as the backend reaches it, when that differs from {@code url}
     */
    public record Keycloak(String url,String realm,String clientId,String backendUrl) {
        public String issuer() { return url+"/realms/"+realm; }
        /** Always follows the realm, so a wrong realm fails the signing-key check before it is saved. */
        public String effectiveJwksUrl() { return (backendUrl==null?url:backendUrl)+"/realms/"+realm+"/protocol/openid-connect/certs"; }
        public Keycloak validated() {
            if (realm==null || !REALM.matcher(realm.strip()).matches())
                throw new IllegalArgumentException("Realm may only contain letters, digits, '.', '_' and '-'");
            if (clientId==null || !CLIENT_ID.matcher(clientId.strip()).matches())
                throw new IllegalArgumentException("Client ID may only contain letters, digits, '.', '_', ':' and '-'");
            return new Keycloak(httpUrl(url,"Keycloak URL"),realm.strip(),clientId.strip(),
                backendUrl==null||backendUrl.isBlank()?null:httpUrl(backendUrl,"Backend address"));
        }
        /**
         * Builds the settings from {@code KEYCLOAK_ISSUER_URI} ({@code <url>/realms/<realm>}) and, optionally,
         * {@code KEYCLOAK_JWKS_URI} ({@code <backend url>/realms/<realm>/protocol/openid-connect/certs}).
         */
        public static Keycloak fromIssuer(String issuer,String clientId,String jwksUrl) {
            int index=issuer.lastIndexOf("/realms/");
            if (index<0) throw new IllegalStateException("KEYCLOAK_ISSUER_URI must look like <Keycloak URL>/realms/<realm>: "+issuer);
            String url=issuer.substring(0,index), realm=issuer.substring(index+"/realms/".length());
            String backendUrl=null;
            if (jwksUrl!=null && !jwksUrl.isBlank()) {
                String suffix="/realms/"+realm+"/protocol/openid-connect/certs";
                if (!jwksUrl.endsWith(suffix))
                    throw new IllegalStateException("KEYCLOAK_JWKS_URI must look like <Keycloak URL>"+suffix+": "+jwksUrl);
                String base=jwksUrl.substring(0,jwksUrl.length()-suffix.length());
                backendUrl=base.equals(url)?null:base;
            }
            return new Keycloak(url,realm,clientId,backendUrl);
        }
    }

    /**
     * @param decisionPath OPA data path of the decision document, e.g. {@code openjcontroltower/decision}
     * @param failOpen     allow actions when OPA cannot be reached; the default denies them
     */
    public record Policy(boolean enabled,String url,String decisionPath,boolean failOpen) {
        public Policy validated() {
            String path=decisionPath==null?"":decisionPath.strip().replaceAll("^/+|/+$","");
            if (!DECISION_PATH.matcher(path).matches())
                throw new IllegalArgumentException("Decision path must look like package/rule, for example openjcontroltower/decision");
            return new Policy(enabled,httpUrl(url,"Open Policy Agent URL"),path,failOpen);
        }
    }

    /** An http(s) URL with a host and without credentials, query, fragment or trailing slash. */
    static String httpUrl(String value,String label) {
        URI uri;
        try { uri=new URI(value==null?"":value.strip()); }
        catch (URISyntaxException e) { throw new IllegalArgumentException(label+" is not a valid URL"); }
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost()==null)
            throw new IllegalArgumentException(label+" must be an http:// or https:// URL with a host");
        if (uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)
            throw new IllegalArgumentException(label+" may not contain credentials, a query or a fragment");
        return uri.toString().replaceAll("/+$","");
    }
}
