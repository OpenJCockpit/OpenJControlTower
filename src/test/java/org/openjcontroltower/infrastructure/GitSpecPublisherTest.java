package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.ResourceAccessException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GitSpecPublisherTest {
    private final CredentialCipher cipher=new CredentialCipher(CredentialCipherTest.SECRET,CredentialCipherTest.SALT);
    private final List<Map<String,String>> environments=new ArrayList<>();
    private final Project privateRepo=new Project("p","Example","https://git.example.org/team/repo.git","develop",
        "HTTPS_TOKEN",null,true,"c","wf");
    private final Spec spec=new Spec("SPEC-1","p","Implement\nlogin","implement-login","DRAFT","# Login",List.of(),
        "001-implement-login/spec.md",null);

    private GitSpecPublisher publisher(GitJobRunner.Result result) throws IOException {
        GitJobRunner runner=(jobId,image,script,env)->{
            assertEquals("alpine/git:test",image);
            assertEquals("echo job",script);
            assertDoesNotThrow(()->UUID.fromString(jobId));
            environments.add(env);
            return result;
        };
        return new GitSpecPublisher(runner,cipher,new ByteArrayResource("echo job".getBytes(StandardCharsets.UTF_8)),
            "alpine/git:test","spec","OpenJControlTower","ct@example.org");
    }
    private static String decode(String value) { return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8); }

    @Test void pushesEveryFileAsItsOwnCommitOnTheSpecBranch() throws IOException {
        GitSpecPublisher.Published published=publisher(new GitJobRunner.Result(0,"cloning\nOJCT_COMMIT=abc123\n"))
            .publish(privateRepo,cipher.encrypt("ghp_secret"),spec);
        assertEquals(new GitSpecPublisher.Published("spec/SPEC-1","abc123"),published);
        Map<String,String> env=environments.getFirst();
        assertEquals("https://git.example.org/team/repo.git",env.get("OJCT_REPOSITORY"));
        assertEquals("develop",env.get("OJCT_BASE_BRANCH"));
        assertEquals("spec/SPEC-1",env.get("OJCT_BRANCH"));
        assertEquals("token",env.get("GIT_USERNAME"));
        assertEquals("ghp_secret",env.get("GIT_PASSWORD"));
        assertEquals("ct@example.org",env.get("GIT_COMMITTER_EMAIL"));
        assertEquals("OpenJControlTower",env.get("GIT_AUTHOR_NAME"));
        assertEquals("5",env.get("OJCT_CHANGES"));
        assertEquals("specs/001-implement-login/spec.md",env.get("OJCT_CHANGE_1_PATH"));
        assertEquals("create",env.get("OJCT_CHANGE_1_MODE"));
        assertEquals("# Login",decode(env.get("OJCT_CHANGE_1_CONTENT")));
        assertEquals("Add specification SPEC-1: Implement login",decode(env.get("OJCT_CHANGE_1_MESSAGE")));
        assertEquals("specs/001-implement-login/review.md",env.get("OJCT_CHANGE_4_PATH"));
        assertNull(env.get("OJCT_CHANGE_4_HEADER"));
        assertEquals("specs/_index.md",env.get("OJCT_CHANGE_5_PATH"));
        assertEquals("append",env.get("OJCT_CHANGE_5_MODE"));
        assertEquals("- [SPEC-1](001-implement-login/spec.md) — Implement login\n",decode(env.get("OJCT_CHANGE_5_CONTENT")));
        assertEquals("# Specification index\n\n",decode(env.get("OJCT_CHANGE_5_HEADER")));
    }
    @Test void usesTheConfiguredUsernameAndNoCredentialsForPublicRepositories() throws IOException {
        Project withUser=new Project("p","E","https://git.example.org/r.git","main","USERNAME_PASSWORD","alice",true,"c","wf");
        publisher(new GitJobRunner.Result(0,"OJCT_COMMIT=1")).publish(withUser,cipher.encrypt("pw"),spec);
        assertEquals("alice",environments.getFirst().get("GIT_USERNAME"));
        Project open=new Project("p","E","https://git.example.org/r.git","main","NONE",null,false,"c","wf");
        publisher(new GitJobRunner.Result(0,"OJCT_COMMIT=2")).publish(open,null,spec);
        assertFalse(environments.get(1).containsKey("GIT_USERNAME"));
        assertFalse(environments.get(1).containsKey("GIT_PASSWORD"));
    }
    @Test void refusesIncompleteConfigurationBeforeStartingAJob() throws IOException {
        GitSpecPublisher publisher=publisher(new GitJobRunner.Result(0,"OJCT_COMMIT=1"));
        Project legacy=new Project("p","E",null,"main","NONE",null,false,"c","wf");
        assertThrows(IllegalArgumentException.class,()->publisher.publish(legacy,null,spec));
        assertThrows(IllegalArgumentException.class,()->publisher.publish(privateRepo,null,spec));
        for (String path:new String[]{"../001-x/spec.md","001-x/../../spec.md","001-x/plan.md","x/spec.md"})
            assertThrows(IllegalArgumentException.class,()->publisher.publish(privateRepo,"c",
                new Spec("S","p","T","t","DRAFT","b",List.of(),path,null)),path);
        assertTrue(environments.isEmpty());
    }
    @Test void reportsKnownFailuresAndNeverLeaksTheSecret() throws IOException {
        String secret=cipher.encrypt("ghp_secret");
        IllegalStateException known=assertThrows(IllegalStateException.class,()->publisher(
            new GitJobRunner.Result(3,"OJCT_ERROR=Branch spec/SPEC-1 already exists on the remote\n")).publish(privateRepo,secret,spec));
        assertEquals("Publishing to Git failed: Branch spec/SPEC-1 already exists on the remote",known.getMessage());
        IllegalStateException raw=assertThrows(IllegalStateException.class,()->publisher(
            new GitJobRunner.Result(128,"fatal: could not read from https://ghp_secret@host\n\n")).publish(privateRepo,secret,spec));
        assertEquals("Publishing to Git failed: fatal: could not read from https://****@host",raw.getMessage());
        IllegalStateException silent=assertThrows(IllegalStateException.class,()->publisher(
            new GitJobRunner.Result(1,"")).publish(privateRepo,secret,spec));
        assertEquals("Publishing to Git failed: the Git job ended without output",silent.getMessage());
        IllegalStateException noCommit=assertThrows(IllegalStateException.class,()->publisher(
            new GitJobRunner.Result(0,"x".repeat(400))).publish(privateRepo,secret,spec));
        assertEquals("Publishing to Git failed: "+"x".repeat(300),noCommit.getMessage());
    }
    @Test void reportsAnUnreachableRunner() throws IOException {
        GitJobRunner unreachable=(id,image,script,env)->{ throw new ResourceAccessException("Connection refused"); };
        GitSpecPublisher publisher=new GitSpecPublisher(unreachable,cipher,new ByteArrayResource(new byte[0]),"i","spec","n","e");
        assertEquals("Could not run the Git job: Connection refused",
            assertThrows(IllegalStateException.class,()->publisher.publish(privateRepo,cipher.encrypt("unique-token"),spec)).getMessage());
        GitJobRunner silent=(id,image,script,env)->{ throw new ResourceAccessException(null); };
        GitSpecPublisher open=new GitSpecPublisher(silent,cipher,new ByteArrayResource(new byte[0]),"i","spec","n","e");
        Project public_=new Project("p","E","https://git.example.org/r.git","main","NONE",null,false,"c","wf");
        assertEquals("Could not run the Git job: ",
            assertThrows(IllegalStateException.class,()->open.publish(public_,null,spec)).getMessage());
    }
}
