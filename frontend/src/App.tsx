import { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, AudioLines, Bot, Check, ChevronRight, CircleHelp, GitBranch, LayoutDashboard, ListChecks, LogOut, Mic, Plus, Radio, RefreshCw, Send, ShieldCheck, Sparkles, Workflow } from 'lucide-react';
import { api, keycloak } from './api/client';
import type { components } from './generated/schema';

type Models=components['schemas'];
type Project=Models['Project'];type Template=Models['Template'];type Question=Models['Question'];type SpecTemplate=Models['SpecTemplate'];
type Interview=Models['Interview'];type Spec=Models['Spec'];type Graph=Models['Graph'];type QueueCandidate=Models['QueueCandidate'];
type Page='overview'|'repositories'|'templates'|'interview'|'specs'|'graph'|'queue';
const nav: {key:Page;label:string;icon:typeof LayoutDashboard}[]=[
  {key:'overview',label:'Overview',icon:LayoutDashboard},
  {key:'repositories',label:'Repositories',icon:GitBranch},
  {key:'templates',label:'Interview templates',icon:ListChecks},
  {key:'interview',label:'Discovery interviews',icon:Bot},
  {key:'specs',label:'Specifications',icon:Workflow},
  {key:'graph',label:'Dependency graph',icon:Activity},
  {key:'queue',label:'Dispatch queue',icon:Send},
];
const sampleQuestions=(kind:'PROJECT'|'FEATURE'):Question[]=> kind==='PROJECT' ? [
  {id:'goal',text:'What is the intended project outcome and who is it for?',required:true,maxFollowUps:2,completionCriteria:'Measurable project goal with target audience'},
  {id:'scope',text:'What is explicitly in and out of scope?',required:true,maxFollowUps:2,completionCriteria:'Concrete scope and non-goals'},
  {id:'quality',text:'Which security, integration and quality criteria are required?',required:true,maxFollowUps:2,completionCriteria:'Concrete technical constraints'},
] : [
  {id:'goal',text:'What exactly should this feature solve, and for which user?',required:true,maxFollowUps:2,completionCriteria:'User goal and value'},
  {id:'behavior',text:'Describe the desired workflow, including failure scenarios.',required:true,maxFollowUps:2,completionCriteria:'Happy path and failure paths'},
  {id:'acceptance',text:'How can we objectively test that this is done?',required:true,maxFollowUps:2,completionCriteria:'Measurable acceptance criteria'},
];
function useApiData(projectId:string, notify:(s:string)=>void) {
  const [projects,setProjects]=useState<Project[]>([]);
  const [templates,setTemplates]=useState<Template[]>([]);
  const [specTemplates,setSpecTemplates]=useState<SpecTemplate[]>([]);
  const [specs,setSpecs]=useState<Spec[]>([]);
  const [interviews,setInterviews]=useState<Interview[]>([]);
  const [graph,setGraph]=useState<Graph>({nodes:[],topologicalOrder:[]});
  const [queue,setQueue]=useState<QueueCandidate[]>([]);
  const refresh=useCallback(async()=>{
    try {
      const [p,t,st]=await Promise.all([api<Project[]>('/projects'),api<Template[]>('/templates'),api<SpecTemplate[]>('/spec-templates')]);
      setProjects(p);setTemplates(t);setSpecTemplates(st);
      if(projectId) {
        const [s,i,g,q]=await Promise.all([
          api<Spec[]>(`/projects/${projectId}/specs`),api<Interview[]>(`/projects/${projectId}/interviews`),
          api<Graph>(`/projects/${projectId}/graph`),api<QueueCandidate[]>(`/projects/${projectId}/queue/proposal`)]);
        setSpecs(s);setInterviews(i);setGraph(g);setQueue(q);
      } else {setSpecs([]);setInterviews([]);setGraph({nodes:[],topologicalOrder:[]});setQueue([]);}
    } catch(e) { notify(String(e)); }
  },[projectId,notify]);
  useEffect(()=>{void refresh();},[refresh]);
  return {projects,templates,specTemplates,specs,interviews,graph,queue,refresh};
}
function GraphView({graph}:{graph:Graph}) {
  const {position,width,height}=useMemo(()=>{
    const positions=new Map<string,{x:number;y:number}>(), levels=new Map<string,number>(), counts=new Map<number,number>();
    for(const id of graph.topologicalOrder) {
      const node=graph.nodes.find(n=>n.id===id);
      const depth=Math.max(0,...(node?.dependsOn||[]).map(d=>(levels.get(d)??-1)+1));
      levels.set(id,depth);const order=counts.get(depth)||0;counts.set(depth,order+1);
      positions.set(id,{x:20+depth*275,y:25+order*112});
    }
    return {position:positions,width:Math.max(320,Math.max(0,...levels.values())*275+260),height:Math.max(160,Math.max(0,...counts.values())*112+35)};
  },[graph]);
  if(!graph.nodes.length) return <Empty text="No specifications yet. Generate your first interview."/>;
  return <div className="graph-scroll"><svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Spec dependency graph">
    <defs><marker id="arrow" markerWidth="9" markerHeight="9" refX="6" refY="3" orient="auto"><path d="M0 0 L6 3 L0 6" stroke="#8c9baf" fill="none"/></marker></defs>
    {graph.nodes.flatMap(node=>node.dependsOn.map(dep=>{
      const a=position.get(dep),b=position.get(node.id);if(!a||!b)return null;
      return <path key={dep+'-'+node.id} d={`M${a.x+216},${a.y+28} C${a.x+248},${a.y+28} ${b.x-25},${b.y+28} ${b.x},${b.y+28}`} stroke="#8c9baf" fill="none" markerEnd="url(#arrow)"/>;
    }))}
    {graph.nodes.map(node=>{const p=position.get(node.id);if(!p)return null;
      return <g key={node.id} transform={`translate(${p.x},${p.y})`}><rect width="216" height="66" rx="9" fill="#151d30" stroke={node.ready?'#45cdbb':'#3c485e'}/>
        <text x="12" y="19" fill="#5bd5c5" fontSize="10">{node.id.substring(0,18)}</text>
        <text x="12" y="36" fill="#f3f5fb" fontSize="12">{node.title.length>27?node.title.substring(0,26)+'…':node.title}</text>
        <text x="12" y="53" fill="#95a7ba" fontSize="10">{node.status}{node.ready?' · READY':''}</text></g>;
    })}
  </svg></div>;
}
function Empty({text}:{text:string}) {return <div className="empty"><CircleHelp size={26}/><p>{text}</p></div>;}
function SpeechButtons({onTranscript,text}:{onTranscript:(s:string)=>void;text:string}) {
  const [listening,setListening]=useState(false);
  const speak=()=>{if(!('speechSynthesis' in window))return;window.speechSynthesis.cancel();const u=new SpeechSynthesisUtterance(text);u.lang=navigator.language;window.speechSynthesis.speak(u);};
  const listen=()=>{
    type Rec={lang:string;onresult:((e:{results:ArrayLike<ArrayLike<{transcript:string}>>})=>void)|null;onend:(()=>void)|null;start:()=>void};
    const win=window as unknown as {SpeechRecognition?:new()=>Rec;webkitSpeechRecognition?:new()=>Rec};
    const Constructor=win.SpeechRecognition||win.webkitSpeechRecognition;
    if(!Constructor){alert('Speech recognition is not available in this browser. You can type instead.');return;}
    const rec=new Constructor();rec.lang=navigator.language;rec.onresult=e=>onTranscript(Array.from(e.results).map(r=>r[0].transcript).join(' '));
    rec.onend=()=>setListening(false);setListening(true);rec.start();
  };
  return <div className="speech"><button type="button" className="btn outline" onClick={listen}><Mic size={15}/>{listening?'Listening…':'Speak'}</button>
    <button type="button" className="btn ghost" onClick={speak}><AudioLines size={15}/>Read question</button></div>;
}
export default function App() {
  const [page,setPage]=useState<Page>('overview');
  const [selectedProject,setSelectedProject]=useState('');
  const [notice,setNotice]=useState('');
  const notify=useCallback((s:string)=>setNotice(s),[]);
  const data=useApiData(selectedProject,notify);
  const [repoForm,setRepoForm]=useState({name:'',rootPath:'',cockpitProjectId:'',workflowId:''});
  const [templateName,setTemplateName]=useState('');
  const [specTemplateName,setSpecTemplateName]=useState('');
  const [specTemplateContent,setSpecTemplateContent]=useState('---\nspec_id: {{SPEC_ID}}\nworkflow_id: {{WORKFLOW_ID}}\ndependencies: {{DEPENDENCIES}}\n---\n# {{TITLE}}\n{{SUMMARY}}\n## Acceptance\n{{ACCEPTANCE_CRITERIA}}');
  const [specTemplateId,setSpecTemplateId]=useState('');
  const [templateKind,setTemplateKind]=useState<'PROJECT'|'FEATURE'>('PROJECT');
  const [questionJson,setQuestionJson]=useState(JSON.stringify(sampleQuestions('PROJECT'),null,2));
  const [templateId,setTemplateId]=useState('');
  const [featureRequest,setFeatureRequest]=useState('');
  const [activeInterview,setActiveInterview]=useState<Interview|null>(null);
  const [answer,setAnswer]=useState('');
  const [busy,setBusy]=useState(false);
  const [published,setPublished]=useState(false);
  const project=data.projects.find(p=>p.id===selectedProject);
  const operation=async(action:()=>Promise<void>)=>{
    setBusy(true);setNotice('');try{await action();await data.refresh();}
    catch(e){notify(e instanceof Error?e.message:String(e));}finally{setBusy(false);}
  };
  const sectionTitle=nav.find(n=>n.key===page)?.label||'Overview';
  return <div className="shell"><aside className="sidebar">
      <div className="brand"><div className="brand-icon"><Radio size={23}/></div><div><strong>OpenJ</strong><span>CONTROL TOWER</span></div></div>
      <div className="nav-heading">WORKSPACE</div>
      <nav>{nav.map(n=><button className={'nav-item '+(page===n.key?'active':'')} key={n.key} onClick={()=>setPage(n.key)}><n.icon size={17}/>{n.label}{page===n.key&&<ChevronRight size={14} className="tail"/>}</button>)}</nav>
      <div className="sidebar-footer"><div className="identity"><ShieldCheck size={17}/><span>{keycloak.tokenParsed?.preferred_username||'Authenticated'}</span></div>
        <button className="btn ghost" onClick={()=>void keycloak.logout()}><LogOut size={15}/>Sign out</button></div>
    </aside><main className="main">
      <header className="topbar"><div className="crumb">Workspace <ChevronRight size={13}/> {sectionTitle}</div><div className="top-actions"><span className="status-dot"/> Connected via Keycloak<button className="icon-btn" onClick={()=>void data.refresh()} title="Refresh"><RefreshCw size={17}/></button></div></header>
      <div className="content"><div className="page-heading"><div><div className="eyebrow">SPECIFICATION OPERATIONS</div><h1>{sectionTitle}</h1><p>Interview-first requirements, dependency-aware specs and governed delivery.</p></div>
        {page!=='repositories'&&<select aria-label="Active project" value={selectedProject} onChange={e=>setSelectedProject(e.target.value)}><option value="">Select project</option>{data.projects.map(p=><option key={p.id} value={p.id}>{p.name}</option>)}</select>}</div>
      {notice&&<div className="notice" role="alert">{notice}<button onClick={()=>setNotice('')}>×</button></div>}
      {page==='overview'&&<><div className="stats"><div className="stat"><GitBranch/><span>Repositories</span><strong>{data.projects.length}</strong></div><div className="stat"><Bot/><span>Interviews</span><strong>{data.interviews.length}</strong></div><div className="stat"><Workflow/><span>Specifications</span><strong>{data.specs.length}</strong></div><div className="stat"><Send/><span>Ready to queue</span><strong>{data.queue.length}</strong></div></div>
        <div className="two-col"><section className="panel"><div className="panel-heading"><h2>How it works</h2><span className="pill">Guided workflow</span></div>
          {['Configure a repository and its OpenJCockpit workflow','Create project and feature interview templates','Interview with adaptive questions or microphone','Generate and review small connected specs','Approve specs, commit to Git, then dispatch ready nodes'].map((s,i)=><div className="step" key={s}><div className="step-number">{i+1}</div>{s}</div>)}</section>
          <section className="panel accent-panel"><Sparkles size={27}/><h2>From ideas to executable specs.</h2><p>Keep each change small, testable and traceable. The DAG blocks unsafe execution order. AI helps articulate requirements; Java validates the rules.</p><button className="btn primary" onClick={()=>setPage('interview')}>Start discovery <ChevronRight size={15}/></button></section></div></>}
      {page==='repositories'&&<div className="two-col"><section className="panel"><h2>Connected repositories</h2>{data.projects.length===0?<Empty text="No repositories connected."/>:data.projects.map(p=><button className={'repo-row '+(selectedProject===p.id?'selected':'')} onClick={()=>{setSelectedProject(p.id);setPage('overview');}} key={p.id}><GitBranch size={20}/><span><b>{p.name}</b><small>{p.rootPath}</small><small>Workflow: {p.workflowId}</small></span><ChevronRight size={16}/></button>)}</section>
        <section className="panel"><h2>Add local Git repository</h2><p className="muted">Only directories under REPOSITORIES_BASE with a .git folder are accepted.</p><form onSubmit={e=>{e.preventDefault();void operation(async()=>{const result=await api<Project>('/projects','POST',repoForm);setSelectedProject(result.id);setRepoForm({name:'',rootPath:'',cockpitProjectId:'',workflowId:''});notify('Repository registered.');});}}>
          {(['name','rootPath','cockpitProjectId','workflowId'] as const).map(k=><label key={k}>{({name:'Project name',rootPath:'Absolute repository path',cockpitProjectId:'OpenJCockpit project ID',workflowId:'OpenJCockpit workflow ID'} as const)[k]}<input required value={repoForm[k]} onChange={e=>setRepoForm({...repoForm,[k]:e.target.value})} placeholder={k==='rootPath'?'/workspace/repos/my-project':''}/></label>)}<button disabled={busy} className="btn primary"><Plus size={16}/>Connect repository</button></form></section></div>}
      {page==='templates'&&<div className="two-col"><section className="panel"><h2>Saved templates</h2>{data.templates.length===0?<Empty text="Create the first template."/>:data.templates.map(t=><div className="template-row" key={t.id}><div><b>{t.name}</b><small>{t.kind} · {t.questions.length} configurable questions</small></div><span className="pill">{t.kind}</span></div>)}</section><section className="panel"><h2>New interview template</h2><form onSubmit={e=>{e.preventDefault();void operation(async()=>{const questions=JSON.parse(questionJson) as Question[];await api<Template>('/templates','POST',{name:templateName,kind:templateKind,questions});setTemplateName('');notify('Template saved.');});}}><label>Template name<input required value={templateName} onChange={e=>setTemplateName(e.target.value)}/></label><label>Interview type<select value={templateKind} onChange={e=>{const kind=e.target.value as 'PROJECT'|'FEATURE';setTemplateKind(kind);setQuestionJson(JSON.stringify(sampleQuestions(kind),null,2));}}><option value="PROJECT">Initial project</option><option value="FEATURE">New feature</option></select></label><label>Questions (JSON)<textarea rows={13} className="code-input" value={questionJson} onChange={e=>setQuestionJson(e.target.value)}/></label><button className="btn primary" disabled={busy}>Save template</button></form></section></div>}
      {page==='templates'&&<section className="panel" style={{marginTop:20}}><div className="panel-heading"><h2>Spec file templates</h2><span className="pill">Markdown format</span></div><p className="muted">Manage reusable Markdown structures separately from interview questionnaires. Supported placeholders: SPEC_ID, WORKFLOW_ID, TITLE, SUMMARY, DEPENDENCIES, ACCEPTANCE_CRITERIA.</p><div className="two-col"><div>{data.specTemplates.map(st=><div className="template-row" key={st.id}><div><b>{st.name}</b><small>{st.defaultTemplate?'Built-in default':'Custom spec template'}</small></div><span className="pill">Markdown</span></div>)}</div><form onSubmit={e=>{e.preventDefault();void operation(async()=>{await api<SpecTemplate>('/spec-templates','POST',{name:specTemplateName,content:specTemplateContent});setSpecTemplateName('');notify('Spec template saved.');});}}><label>Template name<input required value={specTemplateName} onChange={e=>setSpecTemplateName(e.target.value)}/></label><label>Markdown template<textarea rows={11} className="code-input" value={specTemplateContent} onChange={e=>setSpecTemplateContent(e.target.value)}/></label><button className="btn primary" disabled={busy}>Save spec template</button></form></div></section>}
      {page==='interview'&&<div className="two-col"><section className="panel"><div className="panel-heading"><h2>Discovery session</h2><span className="pill">Text + voice</span></div>{!activeInterview ? <form onSubmit={e=>{e.preventDefault();void operation(async()=>{const i=await api<Interview>('/interviews','POST',{projectId:selectedProject,templateId,featureRequest:data.templates.find(t=>t.id===templateId)?.kind==='FEATURE'?featureRequest:null});setActiveInterview(i);setAnswer('');});}}><label>Interview template<select required value={templateId} onChange={e=>setTemplateId(e.target.value)}><option value="">Select template</option>{data.templates.map(t=><option value={t.id} key={t.id}>{t.name} ({t.kind})</option>)}</select></label>{data.templates.find(t=>t.id===templateId)?.kind==='FEATURE'&&<label>New feature request<textarea required rows={4} value={featureRequest} onChange={e=>setFeatureRequest(e.target.value)} placeholder="Describe the feature you'd like to create…"/></label>}<button disabled={!selectedProject||!templateId||busy} className="btn primary"><Plus size={15}/>Start interview</button></form> : <div className="session"><div className="session-label"><span className="status-dot"/> {activeInterview.status} · {activeInterview.kind}</div><h3>{activeInterview.status==='ACTIVE'?activeInterview.currentQuestion:'Interview completed'}</h3>{activeInterview.status==='ACTIVE'?<form onSubmit={e=>{e.preventDefault();void operation(async()=>{const i=await api<Interview>(`/interviews/${activeInterview.id}/answers`,'POST',{answer});setActiveInterview(i);setAnswer('');});}}><textarea rows={5} required value={answer} onChange={e=>setAnswer(e.target.value)} placeholder="Give a concrete answer…"/><SpeechButtons text={activeInterview.currentQuestion} onTranscript={text=>setAnswer(old=>old?(old+' '+text):text)}/><button className="btn primary" disabled={busy}>Submit answer <ChevronRight size={15}/></button></form>:<div><p className="muted">The interview has enough information for a spec proposal.</p><label style={{display:'block',margin:'10px 0 18px'}}>Spec output template<select value={specTemplateId} onChange={e=>setSpecTemplateId(e.target.value)}><option value="">OpenJCockpit default</option>{data.specTemplates.map(t=><option key={t.id} value={t.id}>{t.name}</option>)}</select></label><button className="btn primary" disabled={busy||activeInterview.status!=='COMPLETE'} onClick={()=>void operation(async()=>{await api<Spec[]>(`/interviews/${activeInterview.id}/generate`,'POST',{specTemplateId:specTemplateId||null});setPage('specs');setActiveInterview(null);})}><Sparkles size={16}/>Generate spec drafts</button><button className="btn ghost" onClick={()=>setActiveInterview(null)}>Close session</button></div>}</div>}</section>
        <section className="panel"><h2>Previous interviews</h2>{data.interviews.length===0?<Empty text="No interviews recorded yet."/>:data.interviews.map(i=><button className="repo-row" key={i.id} onClick={()=>{setActiveInterview(i);setAnswer('');}}><Bot size={18}/><span><b>{i.title}</b><small>{i.kind} · {i.status} · {i.answers.length} answers</small></span><ChevronRight size={15}/></button>)}<div className="hint"><CircleHelp size={17}/><span>Browser speech recognition may use a third-party speech service; consult your privacy policy before using sensitive data.</span></div></section></div>}
      {page==='specs'&&<section className="panel"><div className="panel-heading"><h2>Specification registry</h2><span className="pill">{data.specs.length} files</span></div>{data.specs.length===0?<Empty text="Complete an interview and generate your first spec drafts."/>:data.specs.map(s=><details className="spec" key={s.id}><summary><div><b>{s.title}</b><small>{s.id} · specs/{s.specPath}</small></div><span className={'state state-'+s.status.toLowerCase()}>{s.status}</span><ChevronRight size={15}/></summary><div className="spec-details"><p>Dependencies: {s.dependsOn.join(', ')||'None'}</p><pre>{s.body}</pre><div className="actions">{s.status==='DRAFT'&&<button disabled={busy} className="btn primary" onClick={()=>void operation(async()=>{await api<Spec>(`/specs/${s.id}/approve`,'POST');notify('Spec written to repository. Commit and push before dispatch.');})}><Check size={14}/>Approve and write files</button>}{s.status==='DISPATCHED'&&<button className="btn outline" onClick={()=>void operation(async()=>{await api<Spec>(`/specs/${s.id}/complete`,'POST');notify('Manually marked DONE. Verify OpenJCockpit first.');})}>Confirm externally completed</button>}</div></div></details>)}</section>}
      {page==='graph'&&<section className="panel"><div className="panel-heading"><h2>Directed acyclic graph</h2><span className="pill">Validated by Java</span></div><p className="muted">Arrows represent required predecessor completion, not simple associations.</p><GraphView graph={data.graph}/></section>}
      {page==='queue'&&<div className="two-col"><section className="panel"><div className="panel-heading"><h2>Ready work</h2><span className="pill">{data.queue.length} ready</span></div>{data.queue.length===0?<Empty text="Approve specs or finish their dependencies to enable dispatch."/>:data.queue.map((q,i)=><div className="queue-row" key={q.specId}><span className="queue-index">{i+1}</span><div><b>{q.title}</b><small>{q.path}</small><small>Priority {q.priority} · {q.reason}</small></div></div>)}</section><section className="panel"><h2>OpenJCockpit dispatch</h2><div className="hint"><ShieldCheck size={18}/><span>Dispatch is opt-in. Only ready, approved specs are sent. Dependent specs stay blocked until predecessors are DONE.</span></div><label className="checkbox"><input type="checkbox" checked={published} onChange={e=>setPublished(e.target.checked)}/> I have committed and published these spec files to the repository accessible by OpenJCockpit.</label><button disabled={busy||!selectedProject||data.queue.length===0||!published} className="btn primary" onClick={()=>void operation(async()=>{const outcomes=await api<Array<{specId:string;result:string}>>(`/projects/${selectedProject}/queue/dispatch`,'POST',{repositoryPublished:true});notify(outcomes.map(o=>o.result).join(', '));})}><Send size={16}/>Dispatch ready specs</button><p className="muted">Uncertain HTTP outcomes are recorded as UNKNOWN, never automatically retried.</p></section></div>}
      <footer>OpenJControlTower · Apache-2.0 · Contract-first control plane · {project?.name||'No project selected'}</footer></div></main></div>;
}
