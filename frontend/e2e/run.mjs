import {execFileSync, spawnSync} from 'node:child_process'
import {randomUUID} from 'node:crypto'
import {mkdtempSync, writeFileSync, rmSync} from 'node:fs'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
import {setTimeout as delay} from 'node:timers/promises'

const origin = 'http://localhost:15173'
const secret = randomUUID()
const temp = mkdtempSync(join(tmpdir(), 'flowtrack-e2e-'))
const ids = []
const docker = (...args) => execFileSync('docker', args, {encoding:'utf8'}).trim()

async function waitFor(check, service) {
  for (let i=0; i<90; i++) {
    try { if (await check()) return } catch {}
    await delay(1000)
  }
  throw new Error(service + ' did not start')
}

try {
  const users = ['alice','bob'].map(username=>({username, enabled:true, firstName:username,
    lastName:'Test', email:username+'@example.test', emailVerified:true,
    credentials:[{type:'password',value:secret,temporary:false}]}))
  const realmFile = join(temp,'flowtrack-realm.json')
  writeFileSync(realmFile,JSON.stringify({realm:'flowtrack',enabled:true,users,clients:[{
    clientId:'flowtrack-frontend',publicClient:true,standardFlowEnabled:true,
    redirectUris:[origin+'/*'],webOrigins:[origin],attributes:{'pkce.code.challenge.method':'S256'}
  }]}))
  const db=docker('create','-p','127.0.0.1:25432:5432','-e','POSTGRES_DB=flowtrack',
    '-e','POSTGRES_USER=flowtrack','-e','POSTGRES_PASSWORD='+secret,'postgres:16-alpine')
  ids.push(db)
  docker('start',db)
  const kc=docker('create','-p','127.0.0.1:18081:8080','--mount','type=bind,src='+temp+',dst=/opt/keycloak/data/import,readonly','quay.io/keycloak/keycloak:26.0','start-dev','--import-realm')
  ids.push(kc)

  docker('start',kc)
  console.log('Waiting for isolated Keycloak...')
  await waitFor(()=>docker('logs',kc).includes('Listening on: http://0.0.0.0:8080'),'Keycloak')
  const run=spawnSync(process.execPath,['node_modules/@playwright/test/cli.js','test','--config','e2e/playwright.config.ts'],{
    stdio:'inherit',env:{...process.env,FLOWTRACK_E2E_PASSWORD:secret}
  })
  if (run.error) throw run.error
  process.exitCode=run.status ?? 1
} finally {
  for(const id of ids.reverse()) { spawnSync('docker',['rm','-fv',id],{stdio:'ignore'}) }
  rmSync(temp,{recursive:true,force:true})
}
