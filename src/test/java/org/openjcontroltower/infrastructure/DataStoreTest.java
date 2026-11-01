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
    @Test void storesTemplatesWithQuestions() {
        Template template=new Template("t","Discovery","PROJECT",List.of(new Question("goal","Goal?",true,2,"Measurable")));
        store.saveTemplate(template);
        assertEquals(List.of(template),store.templates());
        assertEquals(template,store.template("t"));
        assertThrows(IllegalArgumentException.class,()->store.template("missing"));
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
        assertEquals(List.of(second),store.interviews("p"));
        assertEquals(second,store.interview("i"));
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
