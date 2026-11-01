package org.openjcontroltower.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GitRepositoriesTest {
    @Test void acceptsHttpsAndOptionallyHttpUrls() {
        assertEquals("https://git.example.org/team/repo.git",GitRepositories.gitUrl(" https://git.example.org/team/repo.git ",false));
        assertEquals("http://git-server:8080/git/example.git",GitRepositories.gitUrl("http://git-server:8080/git/example.git",true));
        assertEquals("Git URL must start with https://",
            assertThrows(IllegalArgumentException.class,()->GitRepositories.gitUrl("http://git.example.org/r.git",false)).getMessage());
        assertEquals("Git URL must start with https:// or http://",
            assertThrows(IllegalArgumentException.class,()->GitRepositories.gitUrl("ftp://git.example.org/r.git",true)).getMessage());
    }
    @ParameterizedTest
    @ValueSource(strings={"git@github.com:org/repo.git","git.example.org/repo.git","https://bad host/r.git","https:///repo.git",
        "https://git.example.org","https://git.example.org/","https://user:token@git.example.org/r.git",
        "https://git.example.org/r.git?x=1","https://git.example.org/r.git#main"})
    void rejectsUnsafeOrIncompleteUrls(String url) {
        assertThrows(IllegalArgumentException.class,()->GitRepositories.gitUrl(url,true));
    }
    @Test void validatesBranchNames() {
        assertEquals("main",GitRepositories.branch(" main "));
        assertEquals("spec/SPEC-1",GitRepositories.branch("spec/SPEC-1"));
        for (String invalid:new String[]{"","-x","/x","a..b","a//b","a/","x.lock","x.","a b","a~b"})
            assertThrows(IllegalArgumentException.class,()->GitRepositories.branch(invalid),invalid);
    }
    @Test void acceptsOnlyKnownCredentialTypes() {
        for (String type:GitRepositories.CREDENTIAL_TYPES) assertEquals(type,GitRepositories.credentialType(type));
        assertThrows(IllegalArgumentException.class,()->GitRepositories.credentialType("SSH_KEY"));
    }
}
