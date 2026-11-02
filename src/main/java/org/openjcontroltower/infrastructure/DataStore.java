package org.openjcontroltower.infrastructure;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.openjcontroltower.domain.Models.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** SQL storage is an adapter, not part of the core graph rules. */
@Repository
public class DataStore {
    private final JdbcTemplate db;
    private final JsonMapper mapper;
    public DataStore(JdbcTemplate db, JsonMapper mapper) { this.db = db; this.mapper = mapper; }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (JacksonException e) { throw new IllegalStateException(e); }
    }
    private <T> T single(List<T> records, String type) {
        if (records.isEmpty()) throw new IllegalArgumentException(type + " not found");
        return records.getFirst();
    }
    /** Stores a new project; {@code encryptedSecret} is ciphertext or null. */
    public void saveProject(Project p, String encryptedSecret) {
        db.update("INSERT INTO projects(id,name,git_url,default_branch,credential_type,git_username,encrypted_secret,cockpit_project_id,workflow_id) VALUES(?,?,?,?,?,?,?,?,?)",
            p.id(), p.name(), p.gitUrl(), p.defaultBranch(), p.credentialType(), p.gitUsername(), encryptedSecret, p.cockpitProjectId(), p.workflowId());
    }
    public void updateProject(Project p, String encryptedSecret) {
        db.update("UPDATE projects SET name=?,git_url=?,default_branch=?,credential_type=?,git_username=?,encrypted_secret=?,cockpit_project_id=?,workflow_id=? WHERE id=?",
            p.name(), p.gitUrl(), p.defaultBranch(), p.credentialType(), p.gitUsername(), encryptedSecret, p.cockpitProjectId(), p.workflowId(), p.id());
    }
    public List<Project> projects() {
        return db.query("SELECT * FROM projects ORDER BY name", (r,n) -> new Project(r.getString("id"),
            r.getString("name"), r.getString("git_url"), r.getString("default_branch"), r.getString("credential_type"),
            r.getString("git_username"), r.getString("encrypted_secret") != null, r.getString("cockpit_project_id"), r.getString("workflow_id")));
    }
    /** The stored ciphertext of a project's Git secret, or null when none is set. */
    public String encryptedSecret(String projectId) {
        return single(db.query("SELECT encrypted_secret FROM projects WHERE id=?",(r,n)->Optional.ofNullable(r.getString(1)),projectId),"Project").orElse(null);
    }
    public Project project(String id) { return single(projects().stream().filter(p -> p.id().equals(id)).toList(), "Project"); }
    public void saveTemplate(Template t) {
        db.update("INSERT INTO templates(id,name,kind,questions_json) VALUES(?,?,?,?)", t.id(),t.name(),t.kind(),json(t.questions()));
    }
    public List<Template> templates() {
        return db.query("SELECT * FROM templates ORDER BY name", (r,n) -> new Template(r.getString("id"),
            r.getString("name"),r.getString("kind"),read(r.getString("questions_json"),new TypeReference<List<Question>>(){})));
    }
    public Template template(String id) { return single(templates().stream().filter(t -> t.id().equals(id)).toList(), "Template"); }
    public void saveSpecTemplate(SpecTemplate template) {
        db.update("INSERT INTO spec_templates(id,name,content,is_default) VALUES(?,?,?,?)",
            template.id(),template.name(),template.content(),template.defaultTemplate());
    }
    public List<SpecTemplate> specTemplates() {
        return db.query("SELECT * FROM spec_templates ORDER BY name",(r,n)->new SpecTemplate(
            r.getString("id"),r.getString("name"),r.getString("content"),r.getBoolean("is_default")));
    }
    public SpecTemplate specTemplate(String id) {
        return single(specTemplates().stream().filter(t->t.id().equals(id)).toList(),"Spec template");
    }
    public void saveInterview(Interview i) {
        int updated = db.update("UPDATE interviews SET project_id=?,template_id=?,kind=?,title=?,status=?,answers_json=?,question_index=?,followups=?,current_question=? WHERE id=?",
            i.projectId(),i.templateId(),i.kind(),i.title(),i.status(),json(i.answers()),i.questionIndex(),i.followUps(),i.currentQuestion(),i.id());
        if (updated == 0) {
            db.update("INSERT INTO interviews(id,project_id,template_id,kind,title,status,answers_json,question_index,followups,current_question) VALUES(?,?,?,?,?,?,?,?,?,?)",
                i.id(),i.projectId(),i.templateId(),i.kind(),i.title(),i.status(),json(i.answers()),i.questionIndex(),i.followUps(),i.currentQuestion());
        }
    }
    public List<Interview> interviews(String projectId) {
        return db.query("SELECT * FROM interviews WHERE project_id=? ORDER BY id", (r,n) -> new Interview(r.getString("id"),
            r.getString("project_id"),r.getString("template_id"),r.getString("kind"),r.getString("title"),r.getString("status"),
            read(r.getString("answers_json"),new TypeReference<List<Answer>>(){}),r.getInt("question_index"),
            r.getInt("followups"),r.getString("current_question")),projectId);
    }
    public Interview interview(String id) {
        return single(db.query("SELECT project_id FROM interviews WHERE id=?",(r,n)->r.getString(1),id)
            .stream().flatMap(p -> interviews(p).stream()).filter(i -> i.id().equals(id)).toList(), "Interview");
    }
    public void saveSpec(Spec s) { saveSpec(s,null); }
    /** {@code createdBy}: the user who generated the spec, or null when unknown. */
    public void saveSpec(Spec s, String createdBy) {
        db.update("INSERT INTO specs(id,project_id,title,slug,status,body,dependencies_json,spec_path,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
            s.id(),s.projectId(),s.title(),s.slug(),s.status(),s.body(),json(s.dependsOn()),s.specPath(),createdBy);
    }
    /** The user who generated the spec, or null when unknown. */
    public String specCreator(String id) {
        return single(db.query("SELECT created_by FROM specs WHERE id=?",(r,n)->Optional.ofNullable(r.getString(1)),id),"Spec").orElse(null);
    }
    public void setSpecStatus(String id, String state) { db.update("UPDATE specs SET status=? WHERE id=?",state,id); }
    public void setSpecPublished(String id, String state, String branch, String commit) {
        db.update("UPDATE specs SET status=?,branch=?,commit_sha=? WHERE id=?",state,branch,commit,id);
    }
    public List<Spec> specs(String projectId) {
        return db.query("SELECT * FROM specs WHERE project_id=? ORDER BY id",(r,n)->new Spec(r.getString("id"),
            r.getString("project_id"),r.getString("title"),r.getString("slug"),r.getString("status"),
            r.getString("body"),read(r.getString("dependencies_json"),new TypeReference<List<String>>(){}),
            r.getString("spec_path"),r.getString("branch")),projectId);
    }
    public Spec spec(String id) {
        List<String> projects = db.query("SELECT project_id FROM specs WHERE id=?",(r,n)->r.getString(1),id);
        return single(projects.stream().flatMap(p -> specs(p).stream()).filter(s -> s.id().equals(id)).toList(),"Spec");
    }
    public <T> Optional<T> setting(String name, Class<T> type) {
        return db.query("SELECT value_json FROM settings WHERE name=?",(r,n)->r.getString(1),name).stream()
            .findFirst().map(value -> read(value, type));
    }
    public void saveSetting(String name, Object value, String updatedBy) {
        int updated = db.update("UPDATE settings SET value_json=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE name=?",json(value),updatedBy,name);
        if (updated == 0)
            db.update("INSERT INTO settings(name,value_json,updated_by,updated_at) VALUES(?,?,?,CURRENT_TIMESTAMP)",name,json(value),updatedBy);
    }
    public boolean hasDispatch(String specId) {
        return Optional.ofNullable(db.queryForObject("SELECT COUNT(*) FROM dispatch_log WHERE spec_id=?",Integer.class,specId)).orElse(0)>0;
    }
    public void recordDispatch(String id,String status,String response) {
        db.update("INSERT INTO dispatch_log(spec_id,state,response) VALUES(?,?,?)",id,status,response);
    }
    public void updateDispatch(String id,String status,String response) {
        db.update("UPDATE dispatch_log SET state=?,response=? WHERE spec_id=?",status,response,id);
    }
}
