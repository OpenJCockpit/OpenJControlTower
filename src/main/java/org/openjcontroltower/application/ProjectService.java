package org.openjcontroltower.application;

import org.openjcontroltower.domain.GitRepositories;
import org.openjcontroltower.domain.Models.Project;
import org.openjcontroltower.infrastructure.CredentialCipher;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.GitConnectionChecker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.Locale;
import java.util.UUID;

/** Registers repositories. Secrets are encrypted before storage and are write-only through the API. */
@Service
public class ProjectService {
    /** {@code secret}: null or blank keeps the stored secret on update; it is ignored for credential type NONE. */
    public record Input(String name,String gitUrl,String defaultBranch,String credentialType,String gitUsername,
                        String secret,String cockpitProjectId,String workflowId) {}
    private final DataStore store;
    private final CredentialCipher cipher;
    private final GitConnectionChecker checker;
    private final PolicyGuard policies;
    private final boolean allowHttp;
    public ProjectService(DataStore store,CredentialCipher cipher,GitConnectionChecker checker,PolicyGuard policies,
                          @Value("${controltower.git.allow-http}") boolean allowHttp) {
        this.store=store;this.cipher=cipher;this.checker=checker;this.policies=policies;this.allowHttp=allowHttp;
    }
    public Project create(Input input) {
        Project project=validated(UUID.randomUUID().toString(),input);
        checkSave(project);
        store.saveProject(project,secret(project,input.secret(),null));
        return store.project(project.id());
    }
    public Project update(String id,Input input) {
        Project existing=store.project(id);
        Project project=validated(id,input);
        checkSave(project);
        String stored=store.encryptedSecret(id);
        if (!project.credentialType().equals("NONE") && blank(input.secret()) && stored!=null)
            requireSameHost(existing.gitUrl(),project.gitUrl());
        store.updateProject(project,secret(project,input.secret(),stored));
        return store.project(id);
    }
    /**
     * Checks whether the remote answers as a Git repository and accepts the credentials. Without a new secret,
     * the stored secret of {@code projectId} is used, but only for the host it was entered for.
     */
    public GitConnectionChecker.Result checkConnection(String projectId,String gitUrl,String credentialType,
                                                       String gitUsername,String secret) {
        String url=GitRepositories.gitUrl(gitUrl,allowHttp);
        policies.check("repository.check",PolicyGuard.resource("projectId",projectId,"gitUrl",url,"host",host(url)));
        if (GitRepositories.credentialType(credentialType).equals("NONE")) return checker.check(url,null,null);
        String username=blank(gitUsername)?"token":gitUsername.strip();
        if (!blank(secret)) return checker.check(url,username,secret);
        String stored=blank(projectId)?null:store.encryptedSecret(projectId);
        if (stored==null) throw new IllegalArgumentException("Enter the token or password to check the connection");
        requireSameHost(store.project(projectId).gitUrl(),url);
        return checker.check(url,username,cipher.decrypt(stored));
    }
    private void checkSave(Project project) {
        policies.check("repository.save",PolicyGuard.resource("projectId",project.id(),"name",project.name(),
            "gitUrl",project.gitUrl(),"host",host(project.gitUrl()),"defaultBranch",project.defaultBranch(),
            "credentialType",project.credentialType()));
    }
    private static String host(String url) { return URI.create(url).getHost().toLowerCase(Locale.ROOT); }
    /** A stored secret never travels to another host than the one it was entered for. */
    private static void requireSameHost(String storedUrl,String url) {
        if (storedUrl==null || !origin(storedUrl).equals(origin(url)))
            throw new IllegalArgumentException("The Git host changed: enter the token or password again");
    }
    private static String origin(String url) {
        URI uri=URI.create(url);
        return (uri.getScheme()+"://"+uri.getRawAuthority()).toLowerCase(Locale.ROOT);
    }
    private Project validated(String id,Input input) {
        String branch=blank(input.defaultBranch())?"main":GitRepositories.branch(input.defaultBranch());
        String type=GitRepositories.credentialType(input.credentialType());
        String username=type.equals("NONE")||blank(input.gitUsername())?null:input.gitUsername().strip();
        return new Project(id,input.name().strip(),GitRepositories.gitUrl(input.gitUrl(),allowHttp),branch,type,username,false,
            input.cockpitProjectId().strip(),input.workflowId().strip());
    }
    private String secret(Project project,String secret,String stored) {
        if (project.credentialType().equals("NONE")) return null;
        if (!blank(secret)) return cipher.encrypt(secret);
        if (stored==null) throw new IllegalArgumentException("A token or password is required for credential type "+project.credentialType());
        return stored;
    }
    private static boolean blank(String value) { return value==null||value.isBlank(); }
}
