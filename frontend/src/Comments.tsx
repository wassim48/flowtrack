import {useEffect,useState} from 'react'
import {api} from './api'

type Comment = {id:number;author:string;body:string;createdAt:string}

export function Comments({projectId,number,writable}:{projectId:number;number:number;writable:boolean}){
  const path='/projects/'+projectId+'/issues/'+number+'/comments'
  const [items,setItems]=useState<Comment[]>([])
  const [more,setMore]=useState(false)
  const [busy,setBusy]=useState(true)
  const [error,setError]=useState('')
  useEffect(()=>{
    const controller=new AbortController()
    api<Comment[]>(path,{signal:controller.signal}).then(rows=>{
      if (!controller.signal.aborted){setItems(rows);setMore(rows.length===50)}
    }).catch(()=>{if (!controller.signal.aborted)setError('Chargement des commentaires impossible.')})
      .finally(()=>{if (!controller.signal.aborted)setBusy(false)})
    return ()=>controller.abort()
  },[path])
  async function previous(){
    setBusy(true);setError('')
    try{
      const rows=await api<Comment[]>(path+'?before='+items[items.length-1].id)
      setItems(list=>[...list,...rows]);setMore(rows.length===50)
    } catch {setError('Chargement impossible. Réessayez.')} finally {setBusy(false)}
  }
  return <div className="create"><h2>Commentaires</h2>
    {writable && <form onSubmit={async e=>{
      e.preventDefault()
      const form=e.currentTarget,body=String(new FormData(form).get('body')).trim()
      if (!body || busy)return
      setBusy(true);setError('')
      try {
        const item=await api<Comment>(path,{method:'POST',body:JSON.stringify({body})})
        setItems(list=>[item,...list]);form.reset()
      } catch {setError('Commentaire non envoyé.')} finally {setBusy(false)}
    }}><label>Message<textarea name="body" required maxLength={4000} placeholder="Mentionner un membre avec @nom"/></label>
      <button disabled={busy}>Publier</button></form>}
    {error && <p role="alert">{error}</p>}
    {items.map(c=><div key={c.id}><strong>{c.author}</strong> · <time dateTime={c.createdAt}>{new Date(c.createdAt).toLocaleString('fr-FR')}</time><p style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{c.body}</p></div>)}
    {more && <button onClick={previous} disabled={busy}>Commentaires précédents</button>}
  </div>
}
