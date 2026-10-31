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
    private <T> T single(List<T> records, String type) {
        if (records.isEmpty()) throw new IllegalArgumentException(type + " not found");
        return records.getFirst();
    }
    public void saveProject(Project p) {
        db.update("INSERT INTO projects(id,name,root_path,cockpit_project_id,workflow_id) VALUES(?,?,?,?,?)",
            p.id(), p.name(), p.rootPath(), p.cockpitProjectId(), p.workflowId());
    }
    public List<Project> projects() {
        return db.query("SELECT * FROM projects ORDER BY name", (r,n) -> new Project(r.getString("id"),
            r.getString("name"), r.getString("root_path"), r.getString("cockpit_project_id"), r.getString("workflow_id")));
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
    public void saveSpec(Spec s) {
        db.update("INSERT INTO specs(id,project_id,title,slug,status,body,dependencies_json,spec_path) VALUES(?,?,?,?,?,?,?,?)",
            s.id(),s.projectId(),s.title(),s.slug(),s.status(),s.body(),json(s.dependsOn()),s.specPath());
    }
    public void setSpecStatus(String id, String state) { db.update("UPDATE specs SET status=? WHERE id=?",state,id); }
    public List<Spec> specs(String projectId) {
        return db.query("SELECT * FROM specs WHERE project_id=? ORDER BY id",(r,n)->new Spec(r.getString("id"),
            r.getString("project_id"),r.getString("title"),r.getString("slug"),r.getString("status"),
            r.getString("body"),read(r.getString("dependencies_json"),new TypeReference<List<String>>(){}),
            r.getString("spec_path")),projectId);
    }
    public Spec spec(String id) {
        List<String> projects = db.query("SELECT project_id FROM specs WHERE id=?",(r,n)->r.getString(1),id);
        return single(projects.stream().flatMap(p -> specs(p).stream()).filter(s -> s.id().equals(id)).toList(),"Spec");
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
