import {useEffect, useState, useRef} from 'react'
import {createRoot} from 'react-dom/client'
import {api, auth, ApiError} from './api'
import {columns, labels, transition, type Board, type Ticket} from './board'
import {Comments} from './Comments'
import {Inbox} from './Inbox'
import {Activity} from './Activity'
import {watchBoard} from './watchBoard'
import './style.css'

type Project = {id:number; name:string; projectKey:string}
const message = (e:unknown) => e instanceof Error ? e.message : 'Connexion impossible.'
const priorities:Record<string,string> = {LOW:'Basse',MEDIUM:'Normale',HIGH:'Haute',CRITICAL:'Critique'}
const types:Record<string,string> = {TASK:'Tâche',BUG:'Anomalie',STORY:'Récit utilisateur'}
type Member = {id:number; name:string; role:string; currentUser:boolean}
const roles:Record<string,string> = {MEMBER:'Membre',VIEWER:'Lecture seule',PROJECT_ADMIN:'Administrateur'}
function Team({projectId,projectKey,members,added}:{projectId:number;projectKey:string;members:Member[];added:(member:Member)=>void}) {
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  return <details><summary>Membres ({members.length})</summary><ul>{members.map(m=><li key={m.id}>{m.name} · {roles[m.role]}</li>)}</ul>
    {members.some(m=>m.currentUser && m.role==='PROJECT_ADMIN') && <form className="create" onSubmit={async e=>{
      e.preventDefault()
      if(busy)return
      const form=e.currentTarget, data=new FormData(form)
      setBusy(true);setError('')
      try {
        added(await api<Member>('/projects/'+projectId+'/members',{method:'POST',body:JSON.stringify({email:String(data.get('email')).trim(),role:data.get('role')})}))
        form.reset()
      } catch(e){setError(e instanceof ApiError && e.status===404?'Compte introuvable. Cette personne doit d’abord se connecter à FlowTrack.':e instanceof ApiError && e.status===409?'Cette personne fait déjà partie du projet.':message(e))}
      finally{setBusy(false)}
    }}><label>Adresse e-mail<input type="email" name="email" required/></label><label>Rôle<select name="role">{Object.entries(roles).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
    {error && <p role="alert">{error}</p>}<button disabled={busy}>Ajouter à {projectKey}</button></form>}</details>
}
function ProjectBoard({project}:{project:Project}) {
  const [members,setMembers]=useState<Member[]>([])
  const writable=members.some(m=>m.currentUser && m.role!=='VIEWER')
  const [editing,setEditing] = useState<Ticket|null>(null)
  const [history,setHistory]=useState<Ticket|null>(null)
  const [board,setBoard] = useState<Board|null>(null)
  const [error,setError] = useState('')
  const [busy,setBusy] = useState(false)
  const [feedback,setFeedback] = useState('')
  const [revision,reload] = useState(0)
  useEffect(()=>watchBoard(project.id,()=>reload(n=>n+1)),[project.id])
  useEffect(() => {
    const controller = new AbortController()
    setError(''); setBusy(true)
    Promise.all([api<Board>('/projects/'+project.id+'/board',{signal:controller.signal}),api<Member[]>('/projects/'+project.id+'/members',{signal:controller.signal})])
      .then(([data,team]) => {if (!controller.signal.aborted) {setBoard(data);setMembers(team)}})
      .catch(e => {if (!controller.signal.aborted) setError(message(e))})
      .finally(() => {if (!controller.signal.aborted) setBusy(false)})
    return () => controller.abort()
  },[project.id,revision])
  async function move(ticket:Ticket) {
    const body = transition(ticket)
    if (!body || busy) return
    setBusy(true); setError('')
    try {
      await api('/projects/'+project.id+'/issues/'+ticket.issueNumber+'/status',{method:'PATCH',body:JSON.stringify(body)})
      setFeedback(ticket.issueKey+' déplacé vers « '+labels[body.newStatus]+' ».'); reload(r=>r+1)
    } catch(e) {
      setError(e instanceof ApiError && e.status===409 ? 'Ce ticket a été modifié depuis son affichage. Rechargez le tableau avant de le déplacer.' : message(e))
      setBusy(false)
    }
  }
  return <main><header><div><small>{project.projectKey} / Tableau Kanban</small><h1>{project.name}</h1></div><button onClick={()=>reload(r=>r+1)} disabled={busy}>↻ Actualiser</button></header>
    {error && <p className="notice" role="alert">{error}</p>}
    {!board && !error && <p role="status">Chargement du tableau…</p>}
    <p className="feedback" role="status">{feedback}</p>
    {board && <Team projectKey={project.projectKey} projectId={project.id} members={members} added={m=>setMembers(list=>[...list,m])}/>}
    {board && writable && <NewTicket members={members} projectId={project.id} onCreated={key=>{setFeedback(key+' ajouté dans « À faire ».');reload(r=>r+1)}}/>}
    {board && <div className="board" aria-busy={busy}>{columns.map(status=><section key={status} data-status={status}><h2><i/>{labels[status]} <span>{board[status].length}</span></h2>
    {board[status].map(ticket=><article key={ticket.issueNumber}><small>{ticket.issueKey}</small><h3><button className="ticket-link" onClick={()=>setEditing(ticket)}>{ticket.title}</button></h3><p className="ticket-meta"><span>{types[ticket.issueType] || ticket.issueType}</span><span>Priorité {(priorities[ticket.priority] || ticket.priority).toLowerCase()}</span></p>
    <p>{members.find(m=>m.id===ticket.assigneeId)?.name || 'Non assigné'}</p>
    <button aria-label={'Historique de '+ticket.issueKey} onClick={()=>setHistory(ticket)}>Historique</button>
    {writable && transition(ticket) && <button disabled={busy || !!error} onClick={()=>move(ticket)}>Passer en « {labels[transition(ticket)!.newStatus]} » →</button>}</article>)}
    {!board[status].length && <p className="empty">Aucun ticket</p>}</section>)}</div>}
    {history && <HistoryDialog projectId={project.id} ticket={history} close={()=>setHistory(null)}/>}
    {editing && <TicketEditor key={editing.issueNumber} ticket={editing} members={members} writable={writable} projectId={project.id} close={()=>setEditing(null)} saved={result=>{setFeedback(editing.issueKey+" "+result);setEditing(null);reload(r=>r+1)}}/>}</main>
}
function HistoryDialog({projectId,ticket,close}:{projectId:number;ticket:Ticket;close:()=>void}) {
  const ref=useRef<HTMLDialogElement>(null)
  useEffect(()=>{const dialog=ref.current!;dialog.showModal();return ()=>dialog.close()},[])
  return <dialog ref={ref} aria-label={'Historique de '+ticket.issueKey} onCancel={e=>{e.preventDefault();close()}}>
    <div className="history-heading"><strong>{ticket.issueKey} · {ticket.title}</strong><button onClick={close}>Fermer</button></div>
    <Activity projectId={projectId} number={ticket.issueNumber}/>
  </dialog>
}
function TicketFields({ticket,members}:{ticket?:Ticket;members:Member[]}) {
  return <>
    <label>Titre<input name="title" defaultValue={ticket?.title} required maxLength={255}/></label>
    <label>Description<textarea name="description" rows={5} defaultValue={ticket?.description || ''}/></label>
    {!ticket && <label>Type<select name="issueType" defaultValue="TASK">{Object.entries(types).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>}
    <label>Responsable<select name="assignee" defaultValue={ticket?.assigneeId || ''}><option value="">Non assigné</option>{members.filter(m=>m.role!=='VIEWER').map(m=><option key={m.id} value={m.id}>{m.name}</option>)}</select></label>
    <label>Priorité<select name="priority" defaultValue={ticket?.priority || 'MEDIUM'}>{Object.entries(priorities).map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></label>
  </>
}
function TicketEditor({ticket,projectId,close,saved,members,writable}:{members:Member[];writable:boolean;ticket:Ticket;projectId:number;close:()=>void;saved:(result:string)=>void}) {
  const [error,setError]=useState('')
  const [busy,setBusy]=useState(false)
  const ref=useRef<HTMLDialogElement>(null)
  useEffect(()=>{const dialog=ref.current!;dialog.showModal();return ()=>dialog.close()},[])
  return <dialog ref={ref} onCancel={event=>{event.preventDefault();if(!busy)close()}}>
    <form className="create" onSubmit={async event=>{
      event.preventDefault()
      if(busy)return
      const data=new FormData(event.currentTarget)
      const title=String(data.get('title')).trim()
      if(!title){setError('Saisissez un titre.');return}
      setBusy(true);setError('')
      try {
        await api('/projects/'+projectId+'/issues/'+ticket.issueNumber,{method:'PATCH',body:JSON.stringify({title,description:data.get('description'),priority:data.get('priority'),assignment:{userId:Number(data.get('assignee'))||null},expectedVersion:ticket.version})})
        saved('mis à jour.')
      } catch(e){setError(e instanceof ApiError && e.status===409 ? 'Le ticket a changé. Copiez vos modifications, fermez la fiche et actualisez le tableau.' : message(e))}
      finally{setBusy(false)}
    }}>
      <h2>{ticket.issueKey}</h2><p>{types[ticket.issueType]} · {labels[ticket.status]}</p>
      <fieldset disabled={!writable} style={{border:0,padding:0,display:'grid',gap:12}}><TicketFields ticket={ticket} members={members}/></fieldset>
      {error && <p role="alert">{error}</p>}
      {members.some(m=>m.currentUser && m.role==='PROJECT_ADMIN') && <button type="button" disabled={busy} onClick={async ()=>{
        if (!window.confirm('Supprimer définitivement le ticket '+ticket.issueKey+' ?')) return
        setBusy(true);setError('')
        try {
          await api<void>('/projects/'+projectId+'/issues/'+ticket.issueNumber+'?expectedVersion='+ticket.version,{method:'DELETE'})
          saved('supprimé.')
        } catch(e) {setError(e instanceof ApiError && e.status===409 ? 'Ce ticket a changé. Fermez la fiche et actualisez avant de le supprimer.' : message(e))}
        finally {setBusy(false)}
      }}>Supprimer le ticket</button>}
      {writable && <button disabled={busy}>Enregistrer</button>}<button type="button" disabled={busy} onClick={close}>Fermer</button>
    </form>
    <Comments projectId={projectId} number={ticket.issueNumber} writable={writable}/>
    <Activity projectId={projectId} number={ticket.issueNumber}/>
  </dialog>
}
function FirstProject({onCreated}:{onCreated:(project:Project)=>void}) {
  const [error,setError] = useState('')
  const [busy,setBusy] = useState(false)
  return <form className="create" onSubmit={async event=>{
    event.preventDefault()
    if (busy) return
    const form = new FormData(event.currentTarget)
    const name = String(form.get('name')).trim()
    const projectKey = String(form.get('key')).trim().toUpperCase()
    if (!name) {setError('Saisissez un nom de projet.'); return}
    setBusy(true); setError('')
    try {
      onCreated(await api<Project>('/projects',{method:'POST',body:JSON.stringify({name,projectKey,description:form.get('description')})}))
    } catch(e) {
      setError(e instanceof ApiError && e.status===409 ? 'Cette clé est déjà utilisée. Choisissez une autre clé.' : message(e))
    } finally {setBusy(false)}
  }}>
    <h2>Créer un projet</h2>
    <label>Nom du projet<input name="name" maxLength={150} placeholder="Projet démo" required/></label>
    <label>Clé du projet<input name="key" pattern="[A-Za-z][A-Za-z0-9]{1,9}" minLength={2} maxLength={10} placeholder="DEMO" required aria-describedby="key-help"/></label>
    <label>Description du projet<textarea name="description" rows={3}/></label>
    <small id="key-help">2 à 10 lettres ou chiffres, en commençant par une lettre.</small>
    {error && <p role="alert">{error}</p>}
    <button disabled={busy}>{busy ? 'Création…' : 'Créer le projet'}</button>
  </form>
}
function NewTicket({projectId,onCreated,members}:{members:Member[];projectId:number;onCreated:(key:string)=>void}) {
  const [error,setError] = useState('')
  const [busy,setBusy] = useState(false)
  return <details><summary>+ Ajouter un ticket</summary><form className="create" onSubmit={async event=>{
    event.preventDefault()
    if (busy) return
    const form=event.currentTarget
    const data=new FormData(form)
    const title=String(data.get('title')).trim()
    if (!title) {setError('Saisissez un titre.');return}
    setBusy(true);setError('')
    try {
      const created = await api<Ticket>('/projects/'+projectId+'/issues',{method:'POST',body:JSON.stringify({title,assigneeId:Number(data.get('assignee')) || null,issueType:data.get('issueType'),description:data.get('description'),priority:data.get('priority')})})
      form.reset();const panel = form.closest('details'); if (panel) {panel.open=false;panel.querySelector('summary')?.focus()} onCreated(created.issueKey)
    } catch(e) {setError(message(e))} finally {setBusy(false)}
  }}><TicketFields members={members}/>
  {error && <p role="alert">{error}</p>}<button disabled={busy}>{busy ? 'Création…' : 'Créer le ticket'}</button></form></details>
}
function Workspace(){
  const [creating,setCreating] = useState(false)
  const [projects,setProjects] = useState<Project[]|null>(null)
  const [id,select] = useState(0)
  const [error,setError] = useState('')
  const [username,setUsername]=useState<string>(()=>auth.tokenParsed?.preferred_username || auth.tokenParsed?.email || '')
  const storageKey='flowtrack:project:'+auth.subject
  function chooseProject(id:number){select(id);sessionStorage.setItem(storageKey,String(id));setCreating(false)}
  useEffect(()=>{api<{username:string;email:string}>('/me').then(profile=>{setUsername(profile.username || profile.email);return api<Project[]>('/projects')}).then(rows=>{setProjects(rows);select(rows.find(p=>p.id===Number(sessionStorage.getItem(storageKey)))?.id || rows[0]?.id || 0)}).catch(e=>setError(message(e)))},[])
  function created(p:Project){setProjects(list=>[...(list || []),p]);chooseProject(p.id)}
  const project=projects?.find(p=>p.id===id)
  return <><nav><strong>FlowTrack</strong>{projects?.length ? <label>Projet <select value={id} onChange={e=>{chooseProject(Number(e.target.value))}}>{projects.map(p=><option value={p.id} key={p.id}>{p.name}</option>)}</select></label> : null}{!!projects?.length && <button onClick={()=>setCreating(!creating)}>{creating ? "Retour au tableau" : "+ Nouveau projet"}</button>}<span className="session-account">Compte connecté : <b>{username || 'Chargement…'}</b></span><Inbox/><button onClick={()=>auth.logout({redirectUri:location.origin})}>Déconnexion</button></nav>
  {creating ? <main><FirstProject onCreated={created}/></main> : project ? <ProjectBoard key={project.id} project={project}/> : <main><h1>Mes projets</h1><p role="status">{error || (projects ? 'Aucun projet associé à votre compte.' : 'Chargement des projets…')}</p>{projects?.length===0 && <FirstProject onCreated={created}/>}{error && <button onClick={()=>location.reload()}>Réessayer</button>}</main>}</>
}
const root=createRoot(document.getElementById('root')!)
root.render(<main><p role="status">Connexion à FlowTrack…</p></main>)
auth.init({onLoad:'login-required',pkceMethod:'S256',checkLoginIframe:false}).then(ok=>{
  if (!ok) throw new Error('Connexion requise.')
  root.render(<Workspace/>)
}).catch(()=>root.render(<main><h1>Connexion indisponible</h1><p>Vérifiez que Keycloak est démarré.</p><button onClick={()=>location.assign(location.origin)}>Réessayer</button></main>))
