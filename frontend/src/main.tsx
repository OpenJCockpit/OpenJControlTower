import React from 'react';
import { createRoot } from 'react-dom/client';
import { keycloak } from './api/client';
import App from './App';
import './styles.scss';

const root=createRoot(document.getElementById('root')!);
root.render(<div className="boot">Authenticating with Keycloak…</div>);
keycloak.init({onLoad:'login-required',pkceMethod:'S256',checkLoginIframe:false}).then(authenticated=>{
  if (authenticated) root.render(<React.StrictMode><App/></React.StrictMode>);
  else root.render(<div className="boot">Authentication required.</div>);
}).catch(error=>root.render(<div className="boot">Keycloak login error: {String(error)}</div>));
