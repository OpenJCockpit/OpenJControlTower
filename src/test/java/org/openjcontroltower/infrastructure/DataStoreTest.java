package org.openjcontroltower.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjcontroltower.domain.Models.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.core.exc.StreamWriteException;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataStoreTest {
    private JdbcTemplate jdbc;
    private DataStore store;

    @BeforeEach void migrate() {
        DriverManagerDataSource dataSource=new DriverManagerDataSource(
            "jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc=new JdbcTemplate(dataSource);
        store=new DataStore(jdbc,JsonMapper.builder().build());
    }
    @Test void storesAndFindsProjects() {
        Project zeta=new Project("2","Zeta","https://git.example.org/z.git","main","HTTPS_TOKEN","bot",false,"c2","w2");
        store.saveProject(zeta,"cipher");
        store.saveProject(new Project("1","Alpha","https://git.example.org/a.git","main","NONE",null,false,"c1","w1"),null);
        assertEquals(List.of("Alpha","Zeta"),store.projects().stream().map(Project::name).toList());
        // The secret is reported only as present, never returned.
        assertEquals(new Project("2","Zeta","https://git.example.org/z.git","main","HTTPS_TOKEN","bot",true,"c2","w2"),store.project("2"));
        assertEquals("cipher",store.encryptedSecret("2"));
        assertNull(store.encryptedSecret("1"));
        assertFalse(store.project("1").hasSecret());
        store.updateProject(new Project("2","Zeta 2","https://git.example.org/z2.git","develop","NONE",null,false,"c3","w3"),null);
        assertEquals(new Project("2","Zeta 2","https://git.example.org/z2.git","develop","NONE",null,false,"c3","w3"),store.project("2"));
        assertNull(store.encryptedSecret("2"));
        assertEquals("Project not found",assertThrows(IllegalArgumentException.class,()->store.project("x")).getMessage());
        assertThrows(IllegalArgumentException.class,()->store.encryptedSecret("x"));
    }
    @Test void refusesASecondRepositoryForTheSameCockpitProject() {
        store.saveProject(new Project("1","A","https://git.example.org/a.git","main","NONE",null,false,"c","w"),null);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
            ()->store.saveProject(new Project("2","B","https://git.example.org/b.git","main","NONE",null,false,"c","w"),null));
    }
    @Test void migrationListsDuplicateCockpitProjectsInsteadOfPickingOne() {
        DriverManagerDataSource dataSource=new DriverManagerDataSource(
            "jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(dataSource).target("4").load().migrate();
        JdbcTemplate old=new JdbcTemplate(dataSource);
        for (String id:List.of("1","2","3"))
            old.update("INSERT INTO projects(id,name,cockpit_project_id,workflow_id) VALUES(?,?,?,?)",id,"P"+id,id.equals("3")?"solo":"dup","w");
        var error=assertThrows(Exception.class,()->Flyway.configure().dataSource(dataSource).load().migrate());
        Throwable cause=error;
        while (cause.getCause()!=null) cause=cause.getCause();
        assertTrue(cause.getMessage().contains("dup (2 repositories)"),cause.getMessage());
        assertFalse(cause.getMessage().contains("solo"));
    }
    @Test void migrationDropsStoredKeycloakSettings() {
        DriverManagerDataSource dataSource=new DriverManagerDataSource(
            "jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        Flyway.configure().dataSource(dataSource).target("3").load().migrate();
        JdbcTemplate old=new JdbcTemplate(dataSource);
        for (String name:List.of("keycloak","policy"))
            old.update("INSERT INTO settings(name,value_json,updated_at) VALUES(?,?,CURRENT_TIMESTAMP)",name,"{}");
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertEquals(List.of("policy"),old.queryForList("SELECT name FROM settings",String.class));
    }
    @Test void storesTemplatesWithQuestions() {
        Template template=new Template("t","Discovery","PROJECT",List.of(new Question("goal","Goal?",true,2,"Measurable")));
        store.saveTemplate(template);
        assertEquals(List.of(template),store.templates());
        assertEquals(template,store.template("t"));
        assertThrows(IllegalArgumentException.class,()->store.template("missing"));
    }
    @Test void updatesAndDeletesTemplatesAndCountsInterviews() {
        store.saveTemplate(new Template("t","Old","PROJECT",List.of(new Question("goal","Goal?",true,2,"x")),true));
        assertTrue(store.template("t").builtIn());
        store.updateTemplate(new Template("t","New","FEATURE",List.of()));
        Template updated=store.template("t");
        assertEquals("New",updated.name());assertEquals("FEATURE",updated.kind());assertTrue(updated.builtIn());
        assertEquals(0,store.interviewCountForTemplate("t",false));
        store.deleteTemplate("t");
        assertThrows(IllegalArgumentException.class,()->store.template("t"));
    }
    @Test void storesInterviewTimestamps() {
        java.time.Instant created=java.time.Instant.parse("2026-01-01T10:00:00Z");
        store.saveInterview(new Interview("i","p","t","PROJECT","T","ACTIVE",List.of(),0,0,"Q",created,created));
        java.time.Instant later=created.plusSeconds(60);
        store.saveInterview(new Interview("i","p","t","PROJECT","T","ACTIVE",List.of(),1,0,"Q2",null,later));
        Interview read=store.interviews("p").getFirst();
        assertEquals(created,read.createdAt());assertEquals(later,read.updatedAt());
        assertEquals(1,store.interviewCountForTemplate("t",true));
    }
    @Test void storesSpecTemplates() {
        SpecTemplate template=new SpecTemplate("s","Default","{{SPEC_ID}}",true);
        store.saveSpecTemplate(template);
        assertEquals(List.of(template),store.specTemplates());
        assertEquals(template,store.specTemplate("s"));
        assertThrows(IllegalArgumentException.class,()->store.specTemplate("missing"));
    }
    @Test void insertsThenUpdatesInterviews() {
        Interview first=new Interview("i","p","t","PROJECT","Title","ACTIVE",List.of(),0,0,"Q1");
        store.saveInterview(first);
        Interview second=new Interview("i","p","t","PROJECT","Title","COMPLETE",List.of(new Answer("q","Q1","A")),1,0,"");
        store.saveInterview(second);
        Interview stored=store.interview("i");
        assertEquals(List.of(stored),store.interviews("p"));
        assertNotNull(stored.createdAt());assertNotNull(stored.updatedAt());
        assertEquals(second,new Interview(stored.id(),stored.projectId(),stored.templateId(),stored.kind(),stored.title(),
            stored.status(),stored.answers(),stored.questionIndex(),stored.followUps(),stored.currentQuestion()));
        assertTrue(store.interviews("other").isEmpty());
        assertThrows(IllegalArgumentException.class,()->store.interview("missing"));
    }
    @Test void storesSpecsAndStatusChanges() {
        Spec spec=new Spec("SPEC-1","p","Title","title","DRAFT","# Body",List.of("SPEC-0"),"001-title/spec.md",null);
        store.saveSpec(spec);
        assertNull(store.spec("SPEC-1").branch());
        store.setSpecPublished("SPEC-1","APPROVED","spec/SPEC-1","abc123");
        store.setSpecStatus("SPEC-1","APPROVED");
        Spec stored=store.spec("SPEC-1");
        assertEquals("APPROVED",stored.status());
        assertEquals("spec/SPEC-1",stored.branch());
        assertEquals("abc123",jdbc.queryForObject("SELECT commit_sha FROM specs WHERE id='SPEC-1'",String.class));
        assertEquals(List.of("SPEC-0"),stored.dependsOn());
        assertEquals(List.of(stored),store.specs("p"));
        assertThrows(IllegalArgumentException.class,()->store.spec("missing"));
        assertNull(store.specCreator("SPEC-1"));
        store.saveSpec(new Spec("SPEC-2","p","Two","two","DRAFT","# Two",List.of(),"002-two/spec.md",null),"alice");
        assertEquals("alice",store.specCreator("SPEC-2"));
        assertThrows(IllegalArgumentException.class,()->store.specCreator("missing"));
    }
    @Test void storesSettingsAsJson() {
        record Value(String url,boolean enabled) {}
        assertTrue(store.setting("x",Value.class).isEmpty());
        store.saveSetting("x",new Value("http://a",true),"alice");
        store.saveSetting("x",new Value("http://b",false),"bob");
        assertEquals(new Value("http://b",false),store.setting("x",Value.class).orElseThrow());
        assertEquals("bob",jdbc.queryForObject("SELECT updated_by FROM settings WHERE name='x'",String.class));
        jdbc.update("UPDATE settings SET value_json='not-json'");
        assertThrows(IllegalStateException.class,()->store.setting("x",Value.class));
    }
    @Test void tracksDispatchReservations() {
        assertFalse(store.hasDispatch("SPEC-1"));
        store.recordDispatch("SPEC-1","UNKNOWN","pending");
        assertTrue(store.hasDispatch("SPEC-1"));
        store.updateDispatch("SPEC-1","SENT","{}");
        assertEquals("SENT",jdbc.queryForObject("SELECT state FROM dispatch_log WHERE spec_id='SPEC-1'",String.class));
        assertThrows(RuntimeException.class,()->store.recordDispatch("SPEC-1","UNKNOWN","duplicate reservation"));
    }
    @Test void reportsCorruptStoredJson() {
        jdbc.update("INSERT INTO templates(id,name,kind,questions_json) VALUES('t','T','PROJECT','not-json')");
        assertThrows(IllegalStateException.class,store::templates);
    }
    @Test void reportsSerializationFailures() {
        JsonMapper failing=mock(JsonMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new StreamWriteException(null,"boom"));
        DataStore broken=new DataStore(jdbc,failing);
        assertThrows(IllegalStateException.class,()->broken.saveTemplate(new Template("t","T","PROJECT",List.of())));
    }
}
