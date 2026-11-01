package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class KubernetesGitJobRunnerTest {
    private static final String JOBS="/apis/batch/v1/namespaces/team/jobs";
    private final JsonMapper json=JsonMapper.builder().build();
    private final List<Duration> sleeps=new ArrayList<>();
    @TempDir Path serviceAccount;

    private KubernetesGitJobRunner runner(StubHttpServer api,Duration timeout) throws IOException {
        Files.writeString(serviceAccount.resolve("token"),"sa-token\n");
        return new KubernetesGitJobRunner(api.url(),serviceAccount,"team",Duration.ofSeconds(2),Duration.ofSeconds(2),timeout,sleeps::add);
    }
    private static StubHttpServer cluster(StubHttpServer api,String status) {
        return api.route("POST "+JOBS,201,"{\"metadata\":{\"uid\":\"uid-1\"}}")
            .route("POST /api/v1/namespaces/team/secrets",201,"{}")
            .route("GET "+JOBS+"/ojct-git-j1",200,status)
            .route("GET /api/v1/namespaces/team/pods",200,"{\"items\":[{\"metadata\":{\"name\":\"pod-1\"}}]}")
            .route("GET /api/v1/namespaces/team/pods/pod-1/log",200,"OJCT_COMMIT=abc\n")
            .route("DELETE "+JOBS+"/ojct-git-j1",200,"{}");
    }

    @Test void runsARestrictedJobWithItsInputsInAnOwnedSecret() throws IOException {
        try (StubHttpServer api=cluster(new StubHttpServer(),"{\"status\":{\"succeeded\":1}}")) {
            GitJobRunner.Result result=runner(api,Duration.ofSeconds(60)).run("j1","alpine/git:2.49.1","echo hi",Map.of("GIT_PASSWORD","s3cret"));
            assertEquals(new GitJobRunner.Result(0,"OJCT_COMMIT=abc\n"),result);
            StubHttpServer.Request created=api.request("POST",JOBS);
            assertEquals("Bearer sa-token",created.authorization());
            assertFalse(created.body().contains("s3cret"),"credentials never appear in the Job spec");
            JsonNode job=json.readTree(created.body());
            assertEquals(60,job.at("/spec/activeDeadlineSeconds").asInt());
            assertEquals(0,job.at("/spec/backoffLimit").asInt());
            JsonNode pod=job.at("/spec/template/spec");
            assertFalse(pod.get("automountServiceAccountToken").asBoolean());
            assertTrue(pod.at("/securityContext/runAsNonRoot").asBoolean());
            JsonNode container=pod.get("containers").get(0);
            assertEquals("echo hi",container.get("command").get(2).asText());
            assertEquals("ojct-git-j1",container.at("/envFrom/0/secretRef/name").asText());
            assertTrue(container.at("/securityContext/readOnlyRootFilesystem").asBoolean());
            JsonNode secret=json.readTree(api.request("POST","/api/v1/namespaces/team/secrets").body());
            assertEquals("s3cret",secret.at("/stringData/GIT_PASSWORD").asText());
            assertEquals("uid-1",secret.at("/metadata/ownerReferences/0/uid").asText());
            assertEquals("/api/v1/namespaces/team/pods?labelSelector=job-name%3Dojct-git-j1",api.request("GET","/api/v1/namespaces/team/pods?").path());
            assertEquals(JOBS+"/ojct-git-j1?propagationPolicy=Background",api.request("DELETE",JOBS).path());
            assertTrue(sleeps.isEmpty());
        }
    }
    @Test void reportsFailedJobsWithTheirLogs() throws IOException {
        try (StubHttpServer api=cluster(new StubHttpServer(),"{\"status\":{\"failed\":1}}")) {
            api.route("GET /api/v1/namespaces/team/pods/pod-1/log",200,"");
            assertEquals(new GitJobRunner.Result(1,""),runner(api,Duration.ofSeconds(60)).run("j1","i","s",Map.of()));
        }
        try (StubHttpServer api=cluster(new StubHttpServer(),"{\"status\":{\"failed\":1}}")) {
            api.route("GET /api/v1/namespaces/team/pods",200,"{\"items\":[]}");
            assertEquals("",runner(api,Duration.ofSeconds(60)).run("j1","i","s",Map.of()).logs());
            api.route("GET /api/v1/namespaces/team/pods",200,"{}");
            assertEquals("",runner(api,Duration.ofSeconds(60)).run("j1","i","s",Map.of()).logs());
            api.route("GET /api/v1/namespaces/team/pods",200,"");
            assertEquals("",runner(api,Duration.ofSeconds(60)).run("j1","i","s",Map.of()).logs());
        }
    }
    @Test void pollsUntilTheTimeoutAndStillDeletesTheJob() throws IOException {
        try (StubHttpServer api=cluster(new StubHttpServer(),"{\"status\":{\"active\":1}}")) {
            GitJobRunner.Result result=runner(api,Duration.ofSeconds(4)).run("j1","i","s",Map.of());
            assertEquals(124,result.exitCode());
            assertEquals("OJCT_ERROR=Git job did not finish within 4 seconds",result.logs());
            assertEquals(List.of(Duration.ofSeconds(2),Duration.ofSeconds(2)),sleeps);
            api.request("DELETE",JOBS);
        }
        try (StubHttpServer api=cluster(new StubHttpServer(),"")) {
            api.route("DELETE "+JOBS+"/ojct-git-j1",500,"{}");
            assertEquals(124,runner(api,Duration.ofSeconds(2)).run("j1","i","s",Map.of()).exitCode());
            api.route("GET "+JOBS+"/ojct-git-j1",200,"{}");
            assertEquals(124,runner(api,Duration.ofSeconds(2)).run("j1","i","s",Map.of()).exitCode());
        }
    }
    @Test void stopsWaitingWhenInterrupted() throws IOException {
        try (StubHttpServer api=cluster(new StubHttpServer(),"{}")) {
            Files.writeString(serviceAccount.resolve("token"),"t");
            KubernetesGitJobRunner runner=new KubernetesGitJobRunner(api.url(),serviceAccount,"team",Duration.ofSeconds(2),
                Duration.ofSeconds(2),Duration.ofSeconds(60),d->{ throw new InterruptedException(); });
            assertThrows(IllegalStateException.class,()->runner.run("j1","i","s",Map.of()));
            assertTrue(Thread.interrupted());
        }
    }
    @Test void deletesTheJobWhenTheApiReturnsNoJob() throws IOException {
        try (StubHttpServer api=cluster(new StubHttpServer(),"{}")) {
            api.route("POST "+JOBS,201,"");
            assertThrows(NullPointerException.class,()->runner(api,Duration.ofSeconds(60)).run("j1","i","s",Map.of()));
            api.request("DELETE",JOBS);
        }
    }
    @Test void readsNamespaceTokenAndCaFromTheServiceAccountDirectory() throws IOException {
        Files.writeString(serviceAccount.resolve("namespace"),"from-file\n");
        Files.copy(Path.of("src/test/resources/kubernetes-test-ca.crt"),serviceAccount.resolve("ca.crt"));
        try (StubHttpServer api=new StubHttpServer()) {
            KubernetesGitJobRunner https=new KubernetesGitJobRunner("https://kubernetes.default.svc/",serviceAccount.toString(),"",
                Duration.ofSeconds(1),Duration.ofSeconds(1),Duration.ofSeconds(10));
            assertNotNull(https);
            // No token file yet: every request needs the current token, so the call fails before reaching the API.
            KubernetesGitJobRunner http=new KubernetesGitJobRunner(api.url(),serviceAccount,"",Duration.ofSeconds(1),
                Duration.ofSeconds(1),Duration.ofSeconds(10),sleeps::add);
            assertThrows(IllegalStateException.class,()->http.run("j1","i","s",Map.of()));
            assertTrue(api.requests().isEmpty());
        }
        Path empty=serviceAccount.resolve("empty");
        assertThrows(IllegalStateException.class,()->new KubernetesGitJobRunner("http://x",empty,"",Duration.ofSeconds(1),
            Duration.ofSeconds(1),Duration.ofSeconds(1),sleeps::add));
        Files.writeString(serviceAccount.resolve("ca.crt"),"not a certificate");
        assertThrows(IllegalStateException.class,()->KubernetesGitJobRunner.sslContext(serviceAccount.resolve("ca.crt")));
    }
}
