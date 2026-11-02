import { useCallback, useEffect, useState } from 'react';

export type Page='overview'|'repositories'|'templates'|'interview'|'specs'|'graph'|'queue'|'settings';

/** One bookmarkable path per sidebar section. */
export const pagePaths: Record<Page,string>={
  overview:'/',
  repositories:'/repositories',
  templates:'/templates',
  interview:'/interviews',
  specs:'/specs',
  graph:'/graph',
  queue:'/queue',
  settings:'/settings',
};

export function pageFromPath(pathname:string):Page {
  const normalized=pathname.length>1?pathname.replace(/\/+$/,''):pathname;
  return (Object.keys(pagePaths) as Page[]).find(p=>pagePaths[p]===normalized)||'overview';
}

/** Builds the URL for a page, keeping the selected project so the bookmark reopens the same context. */
export function urlFor(page:Page, project:string):string {
  return pagePaths[page]+(project?'?project='+encodeURIComponent(project):'');
}

export function projectFromSearch(search:string):string {
  return new URLSearchParams(search).get('project')||'';
}

/** Keeps the current page and project in sync with the address bar and the back/forward buttons. */
export function useLocationState() {
  const read=()=>({page:pageFromPath(window.location.pathname),project:projectFromSearch(window.location.search)});
  const [state,setState]=useState(read);
  useEffect(()=>{
    // Unknown paths fall back to the overview; show its canonical URL.
    const {page,project}=read();
    const canonical=urlFor(page,project);
    if (window.location.pathname+window.location.search!==canonical) window.history.replaceState(null,'',canonical);
    const onPop=()=>setState(read());
    window.addEventListener('popstate',onPop);
    return ()=>window.removeEventListener('popstate',onPop);
  },[]);
  const navigate=useCallback((page:Page)=>{
    const project=projectFromSearch(window.location.search);
    if (pageFromPath(window.location.pathname)!==page) window.history.pushState(null,'',urlFor(page,project));
    setState({page,project});
  },[]);
  const selectProject=useCallback((project:string)=>{
    const page=pageFromPath(window.location.pathname);
    window.history.replaceState(null,'',urlFor(page,project));
    setState({page,project});
  },[]);
  return {...state,navigate,selectProject};
}
