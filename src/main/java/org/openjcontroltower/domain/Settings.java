package org.openjcontroltower.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Instance settings that administrators change at runtime: the Open Policy Agent. */
public final class Settings {
    private Settings() {}
    private static final Pattern DECISION_PATH=Pattern.compile("[A-Za-z0-9_]{1,60}(/[A-Za-z0-9_]{1,60}){0,9}");

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
