import {defineConfig} from '@playwright/test'
import {resolve} from 'node:path'
const password=process.env.FLOWTRACK_E2E_PASSWORD
if (!password) throw new Error('Start the isolated environment with npm run test:e2e')
export default defineConfig({
  testDir:'.',testMatch:'*.e2e.ts',workers:1,timeout:90000,expect:{timeout:15000},
  use:{baseURL:'http://localhost:15173',channel:process.env.PLAYWRIGHT_CHANNEL},
  webServer:[
    {command:process.platform==='win32'?'mvnw.cmd spring-boot:run':'bash mvnw spring-boot:run',
      cwd:resolve('../backend/flowtrack-api'),url:'http://127.0.0.1:18080/actuator/health',timeout:150000,
      env:{FLOWTRACK_FRONTENDORIGIN:'http://localhost:15173',SERVER_PORT:'18080',SPRING_DATASOURCE_URL:'jdbc:postgresql://127.0.0.1:25432/flowtrack',POSTGRES_PASSWORD:password,
        SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI:'http://localhost:18081/realms/flowtrack'}},
    {command:'npm run dev:e2e',cwd:resolve('.'),url:'http://localhost:15173',
      env:{VITE_API_URL:'http://localhost:18080',VITE_KEYCLOAK_URL:'http://localhost:18081'}}
  ]
})
