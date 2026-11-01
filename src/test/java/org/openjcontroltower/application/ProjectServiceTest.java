package org.openjcontroltower.application;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openjcontroltower.domain.Models.Project;
import org.openjcontroltower.infrastructure.CredentialCipher;
import org.openjcontroltower.infrastructure.DataStore;
import org.openjcontroltower.infrastructure.GitConnectionChecker;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectServiceTest {
    private final DataStore store=mock(DataStore.class);
    private final CredentialCipher cipher=new CredentialCipher("unit-test-secret-at-least-32-characters","0123456789abcdef");
    private final GitConnectionChecker checker=mock(GitConnectionChecker.class);
    private final ProjectService service=new ProjectService(store,cipher,checker,false);
    private final GitConnectionChecker.Result ok=new GitConnectionChecker.Result(true,"OK","Connected");
    private final Project stored=new Project("id","Repo","https://git.example.org/r.git","main","HTTPS_TOKEN","bot",true,"c","w");

    private static ProjectService.Input input(String type,String username,String secret,String branch) {
        return new ProjectService.Input(" Repo ","https://git.example.org/r.git",branch,type,username,secret," c "," w ");
    }
    private Project saved(ArgumentCaptor<String> secret,boolean update) {
        ArgumentCaptor<Project> project=ArgumentCaptor.forClass(Project.class);
        if (update) verify(store).updateProject(project.capture(),secret.capture());
        else verify(store).saveProject(project.capture(),secret.capture());
        return project.getValue();
    }

    @Test void createsPublicRepositoryWithoutCredentials() {
        when(store.project(anyString())).thenReturn(stored);
        assertSame(stored,service.create(input("NONE","ignored","ignored",null)));
        ArgumentCaptor<String> secret=ArgumentCaptor.forClass(String.class);
        Project project=saved(secret,false);
        assertNull(secret.getValue());
        assertEquals("Repo",project.name());
        assertEquals("main",project.defaultBranch());
        assertNull(project.gitUsername());
        assertEquals("c",project.cockpitProjectId());
        assertEquals("w",project.workflowId());
    }
    @Test void encryptsTheSecretOfAuthenticatedRepositories() {
        when(store.project(anyString())).thenReturn(stored);
        service.create(input("HTTPS_TOKEN"," bot ","ghp_secret","develop"));
        ArgumentCaptor<String> secret=ArgumentCaptor.forClass(String.class);
        Project project=saved(secret,false);
        assertEquals("ghp_secret",cipher.decrypt(secret.getValue()));
        assertEquals("bot",project.gitUsername());
        assertEquals("develop",project.defaultBranch());
    }
    @Test void requiresASecretForAuthenticatedRepositories() {
        assertThrows(IllegalArgumentException.class,()->service.create(input("USERNAME_PASSWORD","u"," ",null)));
        assertThrows(IllegalArgumentException.class,()->service.create(input("HTTPS_TOKEN",null,null,null)));
        verify(store,never()).saveProject(any(),any());
    }
    @Test void validatesUrlBranchAndType() {
        assertThrows(IllegalArgumentException.class,()->service.create(
            new ProjectService.Input("R","http://git.example.org/r.git",null,"NONE",null,null,"c","w")));
        assertThrows(IllegalArgumentException.class,()->service.create(input("NONE",null,null,"bad..branch")));
        assertThrows(IllegalArgumentException.class,()->service.create(input("SSH",null,null,null)));
    }
    @Test void keepsReplacesOrClearsTheStoredSecretOnUpdate() {
        when(store.project("id")).thenReturn(stored);
        when(store.encryptedSecret("id")).thenReturn("stored-cipher");
        service.update("id",input("HTTPS_TOKEN","bot","",null));
        service.update("id",input("HTTPS_TOKEN","bot","new-token",null));
        service.update("id",input("NONE",null,null,null));
        ArgumentCaptor<String> secret=ArgumentCaptor.forClass(String.class);
        verify(store,times(3)).updateProject(any(),secret.capture());
        assertEquals("stored-cipher",secret.getAllValues().get(0));
        assertEquals("new-token",cipher.decrypt(secret.getAllValues().get(1)));
        assertNull(secret.getAllValues().get(2));
    }
    @Test void requiresTheSecretAgainWhenTheGitHostChanges() {
        when(store.project("id")).thenReturn(stored);
        when(store.encryptedSecret("id")).thenReturn("stored-cipher");
        ProjectService.Input otherHost=new ProjectService.Input("Repo","https://attacker.example.net/r.git",null,"HTTPS_TOKEN",null,null,"c","w");
        assertEquals("The Git host changed: enter the token or password again",
            assertThrows(IllegalArgumentException.class,()->service.update("id",otherHost)).getMessage());
        // Another path on the same host keeps the stored secret; a new secret may go anywhere.
        service.update("id",new ProjectService.Input("Repo","https://GIT.example.org/other.git",null,"HTTPS_TOKEN",null,null,"c","w"));
        service.update("id",new ProjectService.Input("Repo","https://attacker.example.net/r.git",null,"HTTPS_TOKEN",null,"new","c","w"));
        verify(store,times(2)).updateProject(any(),any());
        when(store.project("legacy")).thenReturn(new Project("legacy","L",null,"main","HTTPS_TOKEN",null,true,"c","w"));
        when(store.encryptedSecret("legacy")).thenReturn("stored-cipher");
        assertThrows(IllegalArgumentException.class,()->service.update("legacy",input("HTTPS_TOKEN",null,null,null)));
    }
    @Test void checksTheConnectionWithTheEnteredOrStoredSecret() {
        when(checker.check(anyString(),any(),any())).thenReturn(ok);
        assertSame(ok,service.checkConnection(null,"https://git.example.org/r.git","NONE","ignored","ignored"));
        verify(checker).check("https://git.example.org/r.git",null,null);
        service.checkConnection(null,"https://git.example.org/r.git","HTTPS_TOKEN"," ","typed");
        verify(checker).check("https://git.example.org/r.git","token","typed");
        when(store.project("id")).thenReturn(stored);
        when(store.encryptedSecret("id")).thenReturn(cipher.encrypt("from-db"));
        service.checkConnection("id","https://git.example.org/other.git","USERNAME_PASSWORD"," alice ",null);
        verify(checker).check("https://git.example.org/other.git","alice","from-db");
    }
    @Test void neverSendsAStoredSecretToAnotherHostOrWithoutAProject() {
        when(store.project("id")).thenReturn(stored);
        when(store.encryptedSecret("id")).thenReturn(cipher.encrypt("from-db"));
        assertEquals("The Git host changed: enter the token or password again",assertThrows(IllegalArgumentException.class,
            ()->service.checkConnection("id","https://attacker.example.net/r.git","HTTPS_TOKEN",null,"")).getMessage());
        assertEquals("Enter the token or password to check the connection",assertThrows(IllegalArgumentException.class,
            ()->service.checkConnection(" ","https://git.example.org/r.git","HTTPS_TOKEN",null,null)).getMessage());
        when(store.encryptedSecret("open")).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->service.checkConnection("open","https://git.example.org/r.git","HTTPS_TOKEN",null,null));
        assertThrows(IllegalArgumentException.class,()->service.checkConnection(null,"http://git.example.org/r.git","NONE",null,null));
        assertThrows(IllegalArgumentException.class,()->service.checkConnection(null,"https://git.example.org/r.git","SSH",null,null));
        verifyNoInteractions(checker);
    }
    @Test void refusesUpdatesWithoutAnySecretOrForUnknownProjects() {
        when(store.project("id")).thenReturn(stored);
        assertThrows(IllegalArgumentException.class,()->service.update("id",input("HTTPS_TOKEN",null,null,null)));
        when(store.project("missing")).thenThrow(new IllegalArgumentException("Project not found"));
        assertThrows(IllegalArgumentException.class,()->service.update("missing",input("NONE",null,null,null)));
    }
}
