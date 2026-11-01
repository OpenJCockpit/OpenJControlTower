package org.openjcontroltower.infrastructure;

import org.openjcontroltower.domain.GitRepositories;
import org.openjcontroltower.domain.Models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Publishes an approved spec to its own branch: an isolated container clones the repository,
 * creates {@code <prefix>/<spec id>}, commits every file separately and pushes. Nothing is written locally.
 */
@Component
public class GitSpecPublisher {
    public record Published(String branch,String commit) {}
    private record Change(String path,String mode,String content,String message) {}
    private static final Pattern SPEC_PATH=Pattern.compile("[0-9]{3}-[a-z0-9-]{1,80}/spec\\.md");
    private static final String INDEX_HEADER="# Specification index\n\n";
    private final GitJobRunner runner;
    private final CredentialCipher cipher;
    private final String script;
    private final String image;
    private final String branchPrefix;
    private final String authorName;
    private final String authorEmail;
    public GitSpecPublisher(GitJobRunner runner,CredentialCipher cipher,
        @Value("classpath:git-job.sh") Resource script,
        @Value("${controltower.git.image}") String image,
        @Value("${controltower.git.branch-prefix}") String branchPrefix,
        @Value("${controltower.git.author-name}") String authorName,
        @Value("${controltower.git.author-email}") String authorEmail) throws IOException {
        this.runner=runner;this.cipher=cipher;this.script=script.getContentAsString(StandardCharsets.UTF_8);
        this.image=image;this.branchPrefix=branchPrefix;this.authorName=authorName;this.authorEmail=authorEmail;
    }
    public Published publish(Project project,String encryptedSecret,Spec spec) {
        if (project.gitUrl()==null) throw new IllegalArgumentException("Configure a Git URL for this repository before approving specs");
        if (!SPEC_PATH.matcher(spec.specPath()).matches()) throw new IllegalArgumentException("Unsafe spec path: "+spec.specPath());
        String folder="specs/"+spec.specPath().substring(0,spec.specPath().indexOf('/'));
        String title=spec.title().replace("\n"," ");
        String branch=GitRepositories.branch(branchPrefix+"/"+spec.id());
        List<Change> changes=List.of(
            new Change(folder+"/spec.md","create",spec.body(),"Add specification "+spec.id()+": "+title),
            new Change(folder+"/plan.md","create","# Plan\n\nTo be developed during implementation.\n","Add implementation plan for "+spec.id()),
            new Change(folder+"/tasks.md","create","# Tasks\n\n- [ ] Implement acceptance criteria.\n","Add task list for "+spec.id()),
            new Change(folder+"/review.md","create","# Review\n\nPending implementation.\n","Add review checklist for "+spec.id()),
            new Change("specs/_index.md","append","- ["+spec.id()+"]("+spec.specPath()+") — "+title+"\n","Register "+spec.id()+" in the specification index"));

        Map<String,String> env=new LinkedHashMap<>();
        env.put("OJCT_REPOSITORY",project.gitUrl());
        env.put("OJCT_BASE_BRANCH",project.defaultBranch());
        env.put("OJCT_BRANCH",branch);
        for (String role:List.of("AUTHOR","COMMITTER")) {
            env.put("GIT_"+role+"_NAME",authorName);
            env.put("GIT_"+role+"_EMAIL",authorEmail);
        }
        String secret=null;
        if (!project.credentialType().equals("NONE")) {
            if (encryptedSecret==null) throw new IllegalArgumentException("This repository has no Git token or password configured");
            secret=cipher.decrypt(encryptedSecret);
            env.put("GIT_USERNAME",project.gitUsername()==null?"token":project.gitUsername());
            env.put("GIT_PASSWORD",secret);
        }
        env.put("OJCT_CHANGES",String.valueOf(changes.size()));
        for (int i=0;i<changes.size();i++) {
            Change change=changes.get(i);
            String prefix="OJCT_CHANGE_"+(i+1)+"_";
            env.put(prefix+"PATH",change.path());
            env.put(prefix+"MODE",change.mode());
            env.put(prefix+"CONTENT",base64(change.content()));
            env.put(prefix+"MESSAGE",base64(change.message()));
            if (change.mode().equals("append")) env.put(prefix+"HEADER",base64(INDEX_HEADER));
        }

        GitJobRunner.Result result;
        try { result=runner.run(UUID.randomUUID().toString(),image,script,env); }
        catch (RestClientException e) { throw new IllegalStateException("Could not run the Git job: "+mask(e.getMessage(),secret),e); }
        String logs=mask(result.logs(),secret);
        Optional<String> commit=lineValue(logs,"OJCT_COMMIT=");
        if (result.exitCode()==0 && commit.isPresent()) return new Published(branch,commit.get());
        String reason=lineValue(logs,"OJCT_ERROR=").orElseGet(()->lastLine(logs));
        throw new IllegalStateException("Publishing to Git failed: "+reason);
    }
    private static String base64(String value) { return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static Optional<String> lineValue(String logs,String key) {
        return logs.lines().filter(l->l.startsWith(key)).reduce((a,b)->b).map(l->l.substring(key.length()).strip());
    }
    private static String lastLine(String logs) {
        String line=logs.lines().map(String::strip).filter(l->!l.isEmpty()).reduce((a,b)->b).orElse("the Git job ended without output");
        return line.length()>300?line.substring(0,300):line;
    }
    /** Defence in depth: git never prints the secret, but no message may ever carry it. */
    private static String mask(String text,String secret) {
        String value=text==null?"":text;
        return secret==null||secret.isEmpty()?value:value.replace(secret,"****");
    }
}
