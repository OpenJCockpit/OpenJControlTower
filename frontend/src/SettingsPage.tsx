import { useEffect, useState } from 'react';
import { KeyRound, LogIn, PlugZap, Save, Scale } from 'lucide-react';
import { api, keycloak } from './api/client';
import type { components } from './generated/schema';

type Models=components['schemas'];
type KeycloakSettings=Models['KeycloakSettings'];type PolicySettings=Models['PolicySettings'];
type SettingsView=Models['SettingsView'];type KeycloakCheckResult=Models['KeycloakCheckResult'];type PolicyStatus=Models['PolicyStatus'];
type Status={ok:boolean;message:string;policies?:string[]}|null;

/** The policies shipped in infrastructure/opa/policies; their values are in openjcontroltower/config/data.json. */
const standardPolicies=[
  {name:'Writer roles',text:'Every governed action needs the editor or admin role, even if the backend role check is misconfigured.'},
  {name:'Allowed Git hosts',text:'Repositories can only be saved or checked for approved Git hosts. An empty list allows every host.'},
  {name:'Four-eyes approval',text:'The person who generated a spec may not approve it. Off by default.'},
  {name:'Acceptance criteria',text:'A spec needs at least two acceptance criteria before it is approved.'},
  {name:'Secret detection',text:'Specs that contain a private key or an access token are not pushed to Git.'},
  {name:'Dispatch freeze',text:'Stops all dispatches to OpenJCockpit, for example during a release. Off by default.'},
  {name:'Dispatch roles',text:'Only these roles may send specs to OpenJCockpit.'},
];
const message=(e:unknown)=>e instanceof Error?e.message:String(e);

function StatusLine({status}:{status:Status}) {
  if(!status) return null;
  return <div role="status" className={'connection-check '+(status.ok?'ok':'failed')}>{status.message}
    {status.policies&&status.policies.length>0&&<small className="policy-files">Loaded: {status.policies.join(', ')}</small>}</div>;
}

/** Instance settings: the Keycloak instance users sign in with, and the Open Policy Agent that governs the process. */
export function SettingsPage() {
  const [view,setView]=useState<SettingsView|null>(null);
  const [error,setError]=useState('');
  const [kc,setKc]=useState<KeycloakSettings>({url:'',realm:'',clientId:'',backendUrl:''});
  const [policy,setPolicy]=useState<PolicySettings>({enabled:false,url:'',decisionPath:'',failOpen:false});
  const [kcStatus,setKcStatus]=useState<Status>(null);
  const [policyStatus,setPolicyStatus]=useState<Status>(null);
  const [signInAgain,setSignInAgain]=useState(false);
  const [busy,setBusy]=useState(false);
  useEffect(()=>{
    api<SettingsView>('/settings').then(v=>{setView(v);setKc({...v.keycloak,backendUrl:v.keycloak.backendUrl??''});setPolicy(v.policy);})
      .catch(e=>setError(message(e)));
  },[]);
  const editable=!!view?.editable;
  const run=async(action:()=>Promise<Status>,show:(s:Status)=>void)=>{
    setBusy(true);show(null);
    try { show(await action()); } catch(e) { show({ok:false,message:message(e)}); } finally { setBusy(false); }
  };
  const kcBody=():KeycloakSettings=>({...kc,backendUrl:kc.backendUrl||null});
  const saveKeycloak=()=>run(async()=>{
    const saved=await api<KeycloakSettings>('/settings/keycloak','PUT',kcBody());
    const issuerChanged=saved.url!==view?.keycloak.url||saved.realm!==view?.keycloak.realm;
    if(view) setView({...view,keycloak:saved});
    setSignInAgain(issuerChanged);
    return {ok:true,message:issuerChanged?'Saved. Sign-ins now go to the new Keycloak instance: sign in again to continue.':'Saved.'};
  },setKcStatus);
  const savePolicy=()=>run(async()=>{
    const saved=await api<PolicySettings>('/settings/policy','PUT',policy);
    setPolicy(saved);
    return {ok:true,message:saved.enabled?'Saved. Repository changes, approvals and dispatches are now checked by the policies.':'Saved. Policy checks are off.'};
  },setPolicyStatus);
  if(error) return <div className="notice" role="alert">{error}</div>;
  if(!view) return <section className="panel"><p className="muted">Loading settings…</p></section>;
  return <>
    {!editable&&<div className="hint"><KeyRound size={17}/><span>Only administrators can change these settings. You can view them.</span></div>}
    <div className="two-col">
      <section className="panel"><div className="panel-heading"><h2>Keycloak</h2><span className="pill">Sign-in</span></div>
        <p className="muted">The Keycloak instance and realm users sign in with. The backend accepts tokens from this realm only, so a change applies to everyone at once. Settings are only saved when the realm's signing keys can be fetched. If sign-in fails after a change, restart the backend with KEYCLOAK_IGNORE_SAVED_SETTINGS=true.</p>
        <form onSubmit={e=>{e.preventDefault();void saveKeycloak();}}>
          <fieldset disabled={!editable||busy}>
            <label>Keycloak URL<input required type="url" value={kc.url} onChange={e=>{setKc({...kc,url:e.target.value});setKcStatus(null);}} placeholder="https://id.example.org"/></label>
            <label>Realm<input required value={kc.realm} onChange={e=>{setKc({...kc,realm:e.target.value});setKcStatus(null);}}/></label>
            <label>Client ID<input required value={kc.clientId} onChange={e=>{setKc({...kc,clientId:e.target.value});setKcStatus(null);}}/></label>
            <label>Backend address (optional)<input type="url" value={kc.backendUrl??''} onChange={e=>{setKc({...kc,backendUrl:e.target.value});setKcStatus(null);}}
              placeholder="Only when the backend reaches Keycloak by another address, e.g. http://keycloak:8080"/></label>
          </fieldset>
          {editable&&<div className="actions"><button className="btn primary" disabled={busy}><Save size={16}/>Save Keycloak settings</button>
            <button type="button" className="btn outline" disabled={busy||!kc.url||!kc.realm||!kc.clientId}
              onClick={()=>void run(async()=>{const r=await api<KeycloakCheckResult>('/settings/keycloak/test','POST',kcBody());return {ok:r.reachable,message:r.message};},setKcStatus)}><PlugZap size={16}/>Test connection</button></div>}
          <StatusLine status={kcStatus}/>
          {signInAgain&&<button type="button" className="btn outline" onClick={()=>void keycloak.logout({redirectUri:window.location.origin+'/settings'})}><LogIn size={16}/>Sign in again</button>}
        </form></section>
      <section className="panel"><div className="panel-heading"><h2>Open Policy Agent</h2><span className="pill">{policy.enabled?'Enforcing':'Off'}</span></div>
        <p className="muted">When enabled, the agent decides on saving and checking repositories, approving specs and dispatching to OpenJCockpit. A denial shows the policy's reasons.</p>
        <form onSubmit={e=>{e.preventDefault();void savePolicy();}}>
          <fieldset disabled={!editable||busy}>
            <label className="checkbox"><input type="checkbox" checked={policy.enabled} onChange={e=>{setPolicy({...policy,enabled:e.target.checked});setPolicyStatus(null);}}/> Enforce policies</label>
            <label>Agent URL<input required type="url" value={policy.url} onChange={e=>{setPolicy({...policy,url:e.target.value});setPolicyStatus(null);}} placeholder="http://opa:8181"/></label>
            <label>Decision path<input required value={policy.decisionPath} onChange={e=>{setPolicy({...policy,decisionPath:e.target.value});setPolicyStatus(null);}} placeholder="openjcontroltower/decision"/></label>
            <label className="checkbox"><input type="checkbox" checked={policy.failOpen} onChange={e=>{setPolicy({...policy,failOpen:e.target.checked});setPolicyStatus(null);}}/> Allow actions while the agent cannot be reached (fail open). Off: they are denied.</label>
          </fieldset>
          {editable&&<div className="actions"><button className="btn primary" disabled={busy}><Save size={16}/>Save policy settings</button>
            <button type="button" className="btn outline" disabled={busy||!policy.url||!policy.decisionPath}
              onClick={()=>void run(async()=>{const r=await api<PolicyStatus>('/settings/policy/test','POST',policy);return {ok:r.reachable,message:r.message,policies:r.policies};},setPolicyStatus)}><PlugZap size={16}/>Test connection</button></div>}
          <StatusLine status={policyStatus}/>
        </form></section>
    </div>
    <section className="panel" style={{marginTop:20}}><div className="panel-heading"><h2>Standard policies</h2><span className="pill"><Scale size={11}/> Rego</span></div>
      <p className="muted">Shipped in <code>infrastructure/opa/policies</code>. Tune them in <code>openjcontroltower/config/data.json</code>, or replace them with your own policies under the same decision path.</p>
      {standardPolicies.map(p=><div className="template-row" key={p.name}><div><b>{p.name}</b><small>{p.text}</small></div></div>)}
    </section>
  </>;
}
