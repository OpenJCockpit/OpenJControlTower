package org.openjcontroltower.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Smoke test of a Git remote without the git CLI: one Git smart-HTTP discovery request
 * ({@code GET <url>/info/refs?service=git-upload-pack}) shows whether the host answers, whether it serves a
 * Git repository and whether the credentials are accepted. Nothing is cloned. Redirects are not followed,
 * so credentials only ever go to the host that was entered.
 */
@Component
public class GitConnectionChecker {
    public record Result(boolean reachable,String outcome,String message) {}
    private final HttpClient client;
    private final Duration readTimeout;
    public GitConnectionChecker(@Value("${controltower.http.connect-timeout}") Duration connectTimeout,
                                @Value("${controltower.http.read-timeout}") Duration readTimeout) {
        this.client=HttpClient.newBuilder().connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build();
        this.readTimeout=readTimeout;
    }
    /** @param username and secret are both null for repositories without authentication. */
    public Result check(String gitUrl,String username,String secret) {
        URI uri=URI.create(gitUrl.replaceAll("/+$","")+"/info/refs?service=git-upload-pack");
        HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(readTimeout).GET()
            .header("User-Agent","git/2.49 (OpenJControlTower connection check)");
        boolean authenticated=secret!=null;
        if (authenticated) request.header("Authorization","Basic "+Base64.getEncoder()
            .encodeToString((username+":"+secret).getBytes(StandardCharsets.UTF_8)));
        try {
            HttpResponse<Void> response=client.send(request.build(),HttpResponse.BodyHandlers.discarding());
            return classify(response.statusCode(),response.headers().firstValue("Content-Type").orElse(""),authenticated);
        } catch (IOException e) {
            return new Result(false,"UNREACHABLE","Could not connect to "+uri.getHost()+": "
                +(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false,"UNREACHABLE","The connection check was interrupted");
        }
    }
    private static Result classify(int status,String contentType,boolean authenticated) {
        if (status==200 && contentType.startsWith("application/x-git-upload-pack-advertisement"))
            return new Result(true,"OK",authenticated?"Connected: the repository is reachable and the credentials are accepted."
                :"Connected: the repository is reachable without credentials.");
        if (status==200) return new Result(false,"NOT_A_GIT_REPOSITORY","The server answered, but not as a Git repository. Check the URL.");
        if (status==401 || status==403) return new Result(false,"AUTH_FAILED",authenticated
            ?"The server rejected the credentials (HTTP "+status+")."
            :"The repository requires authentication, or does not exist (HTTP "+status+").");
        if (status==404) return new Result(false,"NOT_FOUND","Repository not found, or the credentials have no access to it.");
        if (status>=300 && status<400) return new Result(false,"REDIRECTED","The server redirects elsewhere (HTTP "+status+"). Use the repository's final clone URL.");
        return new Result(false,"UNEXPECTED_STATUS","The server answered HTTP "+status+".");
    }
}
