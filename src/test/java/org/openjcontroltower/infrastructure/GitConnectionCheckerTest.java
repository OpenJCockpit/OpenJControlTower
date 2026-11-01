package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class GitConnectionCheckerTest {
    private static final String REFS="GET /git/repo.git/info/refs";
    private static final String ADVERTISEMENT="application/x-git-upload-pack-advertisement";
    private final GitConnectionChecker checker=new GitConnectionChecker(Duration.ofSeconds(2),Duration.ofMillis(500));

    private static StubHttpServer git(int status,String contentType) throws IOException {
        return new StubHttpServer().route(REFS,status,"001e# service=git-upload-pack\n".getBytes(StandardCharsets.UTF_8),contentType);
    }

    @Test void acceptsASmartHttpAdvertisementAndSendsBasicAuthOnlyWithASecret() throws IOException {
        try (StubHttpServer server=git(200,ADVERTISEMENT)) {
            GitConnectionChecker.Result result=checker.check(server.url()+"/git/repo.git/","bot","s3cret");
            assertEquals(new GitConnectionChecker.Result(true,"OK","Connected: the repository is reachable and the credentials are accepted."),result);
            StubHttpServer.Request request=server.last();
            assertEquals("/git/repo.git/info/refs?service=git-upload-pack",request.path());
            assertEquals("Basic "+Base64.getEncoder().encodeToString("bot:s3cret".getBytes(StandardCharsets.UTF_8)),request.authorization());
            assertEquals("Connected: the repository is reachable without credentials.",checker.check(server.url()+"/git/repo.git",null,null).message());
            assertNull(server.last().authorization());
        }
    }
    @Test void classifiesEveryOtherAnswer() throws IOException {
        record Case(int status,String contentType,String secret,String outcome,String message) {}
        for (Case c:new Case[]{
            new Case(200,"text/html",null,"NOT_A_GIT_REPOSITORY","The server answered, but not as a Git repository. Check the URL."),
            new Case(401,"text/plain","s","AUTH_FAILED","The server rejected the credentials (HTTP 401)."),
            new Case(403,"text/plain",null,"AUTH_FAILED","The repository requires authentication, or does not exist (HTTP 403)."),
            new Case(404,"text/plain","s","NOT_FOUND","Repository not found, or the credentials have no access to it."),
            new Case(301,"text/plain",null,"REDIRECTED","The server redirects elsewhere (HTTP 301). Use the repository's final clone URL."),
            new Case(500,"text/plain",null,"UNEXPECTED_STATUS","The server answered HTTP 500.")})
            try (StubHttpServer server=git(c.status(),c.contentType())) {
                assertEquals(new GitConnectionChecker.Result(false,c.outcome(),c.message()),
                    checker.check(server.url()+"/git/repo.git",c.secret()==null?null:"u",c.secret()),c.toString());
            }
    }
    @Test void reportsUnreachableHostsTimeoutsAndInterruptions() throws IOException {
        int closedPort;
        try (ServerSocket socket=new ServerSocket(0)) { closedPort=socket.getLocalPort(); }
        GitConnectionChecker.Result refused=checker.check("http://127.0.0.1:"+closedPort+"/repo.git",null,null);
        assertFalse(refused.reachable());
        assertEquals("UNREACHABLE",refused.outcome());
        assertTrue(refused.message().startsWith("Could not connect to 127.0.0.1: "),refused.message());
        try (StubHttpServer slow=git(200,ADVERTISEMENT).delay(1500)) {
            assertEquals("Could not connect to 127.0.0.1: request timed out",checker.check(slow.url()+"/git/repo.git",null,null).message());
        }
        try (StubHttpServer server=git(200,ADVERTISEMENT)) {
            Thread.currentThread().interrupt();
            assertEquals(new GitConnectionChecker.Result(false,"UNREACHABLE","The connection check was interrupted"),
                checker.check(server.url()+"/git/repo.git",null,null));
            assertTrue(Thread.interrupted());
        }
    }
}
