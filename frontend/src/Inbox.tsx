import {useEffect,useState} from 'react'
import {api} from './api'

type Item={id:number;issueKey:string;actor:string;kind:string;occurredAt:string;read:boolean}
type Page={items:Item[];unread:number;nextCursor:number|null}
export function Inbox(){
  const [open,setOpen]=useState(false)
  const [page,setPage]=useState<Page|null>(null)
  const [busy,setBusy]=useState(false)
  const [error,setError]=useState('')
  const [revision,reload]=useState(0)
  useEffect(()=>{
    const ctl=new AbortController()
    setBusy(true);setError('')
    api<Page>('/notifications',{signal:ctl.signal}).then(data=>{if(!ctl.signal.aborted)setPage(data)})
      .catch(()=>{if(!ctl.signal.aborted)setError('Chargement impossible.')})
      .finally(()=>{if(!ctl.signal.aborted)setBusy(false)})
    return ()=>ctl.abort()
  },[open,revision])
  async function read(item:Item){
    setBusy(true);setError('')
    try {
      await api<void>('/notifications/'+item.id+'/read',{method:'PATCH'})
      setPage(p=>p && {...p,unread:Math.max(0,p.unread-1),items:p.items.map(n=>n.id===item.id?{...n,read:true}:n)})
    } catch {setError('La lecture n’a pas été enregistrée.')} finally {setBusy(false)}
  }
  async function more(){
    if(!page?.nextCursor)return
    setBusy(true);setError('')
    try{
      const next=await api<Page>('/notifications?before='+page.nextCursor)
      setPage(p=>p && {...next,items:[...p.items,...next.items]})
    }catch{setError('Chargement impossible.')}finally{setBusy(false)}
  }
  return <div className="inbox"><button aria-expanded={open} onClick={()=>setOpen(!open)}>Notifications{page?.unread?' ('+page.unread+')':''}</button>
    {open && <div className="inbox-panel"><h2>Notifications</h2>
      <button onClick={()=>reload(n=>n+1)} disabled={busy}>Actualiser les notifications</button>
      {error && <p role="alert">{error}</p>}
      {busy && <p role="status">Chargement…</p>}
      {!busy && !error && page?.items.length===0 && <p>Aucune notification.</p>}
      {page?.items.map(item=><article key={item.id}>
        <strong>{item.issueKey}</strong><p>{item.actor} {item.kind==='ISSUE_ASSIGNED'?'vous a assigné ce ticket.':'vous a mentionné dans un commentaire.'}</p>
        <small>{new Date(item.occurredAt).toLocaleString('fr-FR')}</small>
        {item.read ? <p>Lue</p> : <button disabled={busy} onClick={()=>read(item)}>Marquer comme lue</button>}
      </article>)}
      {page?.nextCursor && <button disabled={busy} onClick={more}>Précédentes</button>}
    </div>}
  </div>
}
