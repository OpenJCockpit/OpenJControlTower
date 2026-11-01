import { useEffect, useState } from 'react';
import { PlugZap, Plus, Save } from 'lucide-react';
import type { components } from './generated/schema';

type Project=components['schemas']['Project'];
export type ProjectRequest=components['schemas']['ProjectRequest'];
type CredentialType=components['schemas']['CredentialType'];
export type ConnectionCheckRequest=components['schemas']['ConnectionCheckRequest'];
export type ConnectionCheckResult=components['schemas']['ConnectionCheckResult'];

const empty:ProjectRequest={name:'',gitUrl:'',defaultBranch:'main',credentialType:'NONE',gitUsername:'',secret:'',cockpitProjectId:'',workflowId:''};
const fromProject=(p:Project):ProjectRequest=>({name:p.name,gitUrl:p.gitUrl??'',defaultBranch:p.defaultBranch,credentialType:p.credentialType,
  gitUsername:p.gitUsername??'',secret:'',cockpitProjectId:p.cockpitProjectId,workflowId:p.workflowId});

/** Git repository settings. The secret is write-only: it is never loaded back, and empty on edit keeps the stored one. */
export function RepositoryForm({editing,busy,onSubmit,onCheck,onCancel}:{editing:Project|null;busy:boolean;
  onSubmit:(body:ProjectRequest)=>Promise<boolean>;onCheck:(body:ConnectionCheckRequest)=>Promise<ConnectionCheckResult>;onCancel:()=>void}) {
  const [form,setForm]=useState<ProjectRequest>(empty);
  const [check,setCheck]=useState<{ok:boolean;message:string}|null>(null);
  const [checking,setChecking]=useState(false);
  useEffect(()=>{setForm(editing?fromProject(editing):empty);setCheck(null);},[editing]);
  const set=(patch:Partial<ProjectRequest>)=>{setForm(f=>({...f,...patch}));setCheck(null);};
  const checkConnection=async()=>{
    setChecking(true);setCheck(null);
    try {
      const result=await onCheck({gitUrl:form.gitUrl,credentialType:form.credentialType,gitUsername:form.gitUsername||undefined,
        secret:form.secret||undefined,projectId:editing?.id});
      setCheck({ok:result.reachable,message:result.message});
    } catch (e) { setCheck({ok:false,message:e instanceof Error?e.message:String(e)}); }
    finally { setChecking(false); }
  };
  const authenticated=form.credentialType!=='NONE';
  const keepsSecret=!!editing&&editing.hasSecret&&editing.credentialType!=='NONE';
  return <form onSubmit={e=>{e.preventDefault();void onSubmit({...form,secret:form.secret||undefined}).then(ok=>{if(ok&&!editing)setForm(empty);});}}>
    <label>Project name<input required value={form.name} onChange={e=>set({name:e.target.value})}/></label>
    <label>Git repository URL<input required type="url" value={form.gitUrl} onChange={e=>set({gitUrl:e.target.value})} placeholder="https://github.com/org/repo.git"/></label>
    <label>Base branch<input value={form.defaultBranch} onChange={e=>set({defaultBranch:e.target.value})} placeholder="main"/></label>
    <label>Authentication<select value={form.credentialType} onChange={e=>set({credentialType:e.target.value as CredentialType})}>
      <option value="NONE">None (public repository)</option><option value="HTTPS_TOKEN">HTTPS access token</option><option value="USERNAME_PASSWORD">Username and password</option></select></label>
    {authenticated&&<>
      <label>Git username<input value={form.gitUsername} onChange={e=>set({gitUsername:e.target.value})} autoComplete="off"
        placeholder={form.credentialType==='HTTPS_TOKEN'?'Optional, defaults to "token"':''} required={form.credentialType==='USERNAME_PASSWORD'}/></label>
      <label>{form.credentialType==='HTTPS_TOKEN'?'Access token':'Password'}<input type="password" autoComplete="new-password" value={form.secret}
        onChange={e=>set({secret:e.target.value})} required={!keepsSecret} placeholder={keepsSecret?'Leave empty to keep the current secret':'Required'}/></label>
    </>}
    <label>OpenJCockpit project ID<input required value={form.cockpitProjectId} onChange={e=>set({cockpitProjectId:e.target.value})}/></label>
    <label>OpenJCockpit workflow ID<input required value={form.workflowId} onChange={e=>set({workflowId:e.target.value})}/></label>
    <div className="actions"><button disabled={busy} className="btn primary">{editing?<><Save size={16}/>Save repository</>:<><Plus size={16}/>Connect repository</>}</button>
      <button type="button" className="btn outline" disabled={busy||checking||!form.gitUrl} onClick={()=>void checkConnection()}><PlugZap size={16}/>{checking?'Checking…':'Check connection'}</button>
      {editing&&<button type="button" className="btn ghost" onClick={onCancel}>Cancel</button>}</div>
    {check&&<div role="status" className={'connection-check '+(check.ok?'ok':'failed')}>{check.message}</div>}
  </form>;
}
