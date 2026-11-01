package org.openjcontroltower.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

/** Rules for the Git settings of a project, checked before anything reaches a Git job. */
public final class GitRepositories {
    private GitRepositories() {}
    public static final List<String> CREDENTIAL_TYPES=List.of("NONE","HTTPS_TOKEN","USERNAME_PASSWORD");
    private static final Pattern BRANCH=Pattern.compile("[A-Za-z0-9._][A-Za-z0-9._/-]{0,199}");

    /** HTTPS only (plain HTTP when explicitly allowed for local development), without embedded credentials. */
    public static String gitUrl(String value,boolean allowHttp) {
        URI uri;
        try { uri=new URI(value.strip()); }
        catch (URISyntaxException e) { throw new IllegalArgumentException("Git URL is not a valid URL"); }
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase();
        if (!scheme.equals("https") && !(allowHttp && scheme.equals("http")))
            throw new IllegalArgumentException(allowHttp?"Git URL must start with https:// or http://":"Git URL must start with https://");
        if (uri.getHost()==null || uri.getPath()==null || uri.getPath().length()<2)
            throw new IllegalArgumentException("Git URL needs a host and a repository path");
        if (uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)
            throw new IllegalArgumentException("Git URL may not contain credentials, a query or a fragment; use the credential fields");
        return uri.toString();
    }
    public static String branch(String value) {
        String branch=value.strip();
        if (!BRANCH.matcher(branch).matches() || branch.contains("..") || branch.contains("//")
            || branch.endsWith("/") || branch.endsWith(".lock") || branch.endsWith("."))
            throw new IllegalArgumentException("Invalid branch name: "+branch);
        return branch;
    }
    public static String credentialType(String value) {
        if (!CREDENTIAL_TYPES.contains(value))
            throw new IllegalArgumentException("Credential type must be one of "+CREDENTIAL_TYPES);
        return value;
    }
}
