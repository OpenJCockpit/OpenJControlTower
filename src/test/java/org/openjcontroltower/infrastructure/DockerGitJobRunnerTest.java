package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DockerGitJobRunnerTest {
    private final JsonMapper json=JsonMapper.builder().build();

    private static DockerGitJobRunner runner(StubHttpServer docker,String network) {
        return new DockerGitJobRunner(docker.url()+"/",network,Duration.ofSeconds(2),Duration.ofSeconds(5));
    }
    private static byte[] frames(String... chunks) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for (int i=0;i<chunks.length;i++) {
            byte[] payload=chunks[i].getBytes(StandardCharsets.UTF_8);
            out.writeBytes(ByteBuffer.allocate(8).put(0,(byte)(i%2+1)).putInt(4,payload.length).array());
            out.writeBytes(payload);
        }
        return out.toByteArray();
    }
    private static StubHttpServer dockerWithContainer(StubHttpServer docker) {
        return docker.route("GET /images/alpine/git:2.49.1/json",200,"{}")
            .route("POST /containers/create",201,"{\"Id\":\"c1\"}")
            .route("POST /containers/c1/start",204,"")
            .route("POST /containers/c1/wait",200,"{\"StatusCode\":0}")
            .route("GET /containers/c1/logs",200,frames("cloning\n","OJCT_COMMIT=abc\n"))
            .route("DELETE /containers/c1",204,"");
    }

    @Test void runsALockedDownContainerAndAlwaysRemovesIt() throws IOException {
        try (StubHttpServer docker=dockerWithContainer(new StubHttpServer())) {
            GitJobRunner.Result result=runner(docker,"").run("job-1","alpine/git:2.49.1","echo hi",Map.of("GIT_PASSWORD","s3cret"));
            assertEquals(new GitJobRunner.Result(0,"cloning\nOJCT_COMMIT=abc\n"),result);
            StubHttpServer.Request create=docker.request("POST","/containers/create");
            assertEquals("/containers/create?name=ojct-git-job-1",create.path());
            JsonNode spec=json.readTree(create.body());
            assertEquals("alpine/git:2.49.1",spec.get("Image").asText());
            assertEquals("echo hi",spec.get("Cmd").get(0).asText());
            assertEquals("10001:10001",spec.get("User").asText());
            assertEquals("job-1",spec.get("Labels").get(DockerGitJobRunner.LABEL).asText());
            assertEquals("bridge",spec.get("Labels").get(DockerGitJobRunner.OWNER_LABEL).asText());
            assertTrue(spec.get("Env").toString().contains("GIT_PASSWORD=s3cret"));
            assertTrue(spec.get("Env").toString().contains("HOME=/work"));
            JsonNode host=spec.get("HostConfig");
            assertTrue(host.get("ReadonlyRootfs").asBoolean());
            assertEquals("ALL",host.get("CapDrop").get(0).asText());
            assertEquals("no-new-privileges",host.get("SecurityOpt").get(0).asText());
            assertEquals("bridge",host.get("NetworkMode").asText());
            assertTrue(host.get("Tmpfs").get("/work").asText().contains("noexec"));
            assertEquals("/containers/c1?force=true",docker.request("DELETE","/containers/c1").path());
        }
    }
    @Test void pullsAMissingImageAndJoinsTheConfiguredNetwork() throws IOException {
        try (StubHttpServer docker=dockerWithContainer(new StubHttpServer())) {
            docker.route("GET /images/alpine/git:2.49.1/json",404,"{}").route("POST /images/create",200,"{\"status\":\"done\"}");
            docker.route("GET /containers/c1/logs",200,new byte[0]);
            assertEquals("",runner(docker,"ojct_default").run("j","alpine/git:2.49.1","s",Map.of()).logs());
            assertEquals("/images/create?fromImage=alpine/git&tag=2.49.1",docker.request("POST","/images/create").path());
            assertEquals("ojct_default",json.readTree(docker.request("POST","/containers/create").body()).get("HostConfig").get("NetworkMode").asText());
        }
        for (String image:new String[]{"busybox","registry:5000/team/git"}) try (StubHttpServer docker=new StubHttpServer()) {
            docker.respond(404,"");
            assertThrows(RuntimeException.class,()->runner(docker,"").run("j",image,"s",Map.of()));
            assertEquals("/images/create?fromImage="+image,docker.request("POST","/images/create").path());
        }
    }
    @Test void removesTheContainerWhenWaitingFailsAndToleratesFailedRemoval() throws IOException {
        try (StubHttpServer docker=dockerWithContainer(new StubHttpServer())) {
            docker.route("POST /containers/c1/wait",200,"").route("DELETE /containers/c1",500,"{}");
            assertThrows(NullPointerException.class,()->runner(docker,"").run("j","alpine/git:2.49.1","s",Map.of()));
            docker.request("DELETE","/containers/c1");
        }
        try (StubHttpServer docker=dockerWithContainer(new StubHttpServer())) {
            docker.route("POST /containers/create",201,"");
            assertThrows(NullPointerException.class,()->runner(docker,"").run("j","alpine/git:2.49.1","s",Map.of()));
        }
    }
    @Test void removesOrphanedJobContainersOnStartup() throws IOException {
        try (StubHttpServer docker=new StubHttpServer()) {
            docker.route("GET /containers/json",200,"[{\"Id\":\"o1\"},{\"Id\":\"o2\"}]").route("DELETE /containers/o1",204,"").route("DELETE /containers/o2",204,"");
            runner(docker,"ojct").removeOrphans();
            assertEquals("/containers/json?all=true&filters=%7B%22label%22:%5B%22openjcontroltower.git-job%22,%22openjcontroltower.owner%3Dojct%22%5D%7D",
                docker.request("GET","/containers/json").path());
            docker.request("DELETE","/containers/o2");
        }
        try (StubHttpServer docker=new StubHttpServer()) {
            docker.respond(200,"");
            runner(docker,"").removeOrphans();
            assertEquals(1,docker.requests().size());
            docker.respond(500,"{}");
            assertDoesNotThrow(()->runner(docker,"").removeOrphans());
        }
    }
    @Test void demultiplexesDockerLogStreams() {
        assertEquals("ab",DockerGitJobRunner.demultiplex(frames("a","b")));
        // A truncated frame yields what is there; trailing bytes shorter than a header are ignored.
        byte[] truncated=ByteBuffer.allocate(11).put(0,(byte)1).putInt(4,10).put(8,(byte)'x').put(9,(byte)'y').put(10,(byte)'z').array();
        assertEquals("xyz",DockerGitJobRunner.demultiplex(truncated));
        assertEquals("",DockerGitJobRunner.demultiplex(new byte[]{1,0,0}));
    }
}
