import {useEffect,useState} from 'react'
import {api} from './api'
import {labels,type Status} from './board'
type Entry={id:string;kind:string;actor:string;target:string|null;oldStatus:Status;newStatus:Status;at:string}
type Page={items:Entry[];nextCursor:string|null}
function action(e:Entry){
  switch(e.kind){
    case 'ISSUE_CREATED':return 'a créé le ticket'
    case 'ISSUE_ASSIGNED':return e.target?'a assigné le ticket à '+e.target:'a retiré le responsable'
    case 'ISSUE_STATUS_CHANGED':return 'a déplacé le ticket de « '+labels[e.oldStatus]+' » vers « '+labels[e.newStatus]+' »'
    case 'COMMENT_CREATED':return 'a publié un commentaire'
    case 'COMMENT_MENTIONED':return 'a mentionné '+e.target
    case 'ISSUE_UPDATED':return 'a modifié la fiche'
    default:return 'a supprimé le ticket'
  }
}
export function Activity({projectId,number}:{projectId:number;number:number}){
  const path='/projects/'+projectId+'/issues/'+number+'/activity'
  const [page,setPage]=useState<Page|null>(null)
  const [error,setError]=useState(false)
  const [busy,setBusy]=useState(true)
  const [revision,refresh]=useState(0)
  useEffect(()=>{
    const ctl=new AbortController()
    setBusy(true);setError(false)
    api<Page>(path,{signal:ctl.signal}).then(p=>{if(!ctl.signal.aborted)setPage(p)})
      .catch(()=>{if(!ctl.signal.aborted)setError(true)})
      .finally(()=>{if(!ctl.signal.aborted)setBusy(false)})
    return ()=>ctl.abort()
  },[path,revision])
  async function more(){
    if(!page?.nextCursor || busy)return
    setBusy(true);setError(false)
    try{
      const next=await api<Page>(path+'?before='+page.nextCursor)
      setPage({...next,items:[...page.items,...next.items]})
    }catch{setError(true)}finally{setBusy(false)}
  }
  return <div className="create"><h2>Historique</h2>
    <button disabled={busy} onClick={()=>refresh(n=>n+1)}>Actualiser l’historique</button>
    {busy && <p role="status">Chargement…</p>}
    {error && <p role="alert">Historique indisponible.</p>}
    {!error && page?.items.length===0 && <p>Aucun événement enregistré pour ce ticket. Les actions antérieures à l’activation de l’historique ne sont pas disponibles.</p>}
    <ol>{page?.items.map(e=><li key={e.id}><strong>{e.actor}</strong> {action(e)}<br/><time dateTime={e.at}>{new Date(e.at).toLocaleString('fr-FR')}</time></li>)}</ol>
    {page?.nextCursor && <button disabled={busy} onClick={more}>Activité précédente</button>}
  </div>
}
