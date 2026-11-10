import {auth} from './api'

// A ready event also catches up changes missed while disconnected.
export function watchBoard(project:number, refresh:()=>void) {
  const ctl = new AbortController()
  let retry:ReturnType<typeof setTimeout>
  let update:ReturnType<typeof setTimeout>
  async function connect(){
    try {
      await auth.updateToken(30)
      if(ctl.signal.aborted || !auth.token) return
      const origin=import.meta.env.VITE_API_URL || 'http://localhost:8080'
      const res=await fetch(origin+'/api/events/projects/'+project,{
        headers:{Authorization:'Bearer '+auth.token,Accept:'text/event-stream'},signal:ctl.signal
      })
      if(res.status===403){refresh();return}
      if(!res.ok || !res.body)throw new Error('Stream unavailable')
      const reader=res.body.pipeThrough(new TextDecoderStream()).getReader()
      let pending=''
      while(!ctl.signal.aborted){
        const {done,value}=await reader.read()
        if(done)break
        pending+=value
        let end
        while((end=pending.indexOf('\n'))>=0){
          const line=pending.slice(0,end).trimEnd();pending=pending.slice(end+1)
          if(/^event:\s*(ready|board)$/.test(line)){
            clearTimeout(update)
            update=setTimeout(refresh,80)
          }
        }
      }
    } catch { /* Retry after disconnect, including token expiry. */ }
    if(!ctl.signal.aborted) retry=setTimeout(connect,2000)
  }
  void connect()
  return ()=>{ctl.abort();clearTimeout(update);clearTimeout(retry)}
}
