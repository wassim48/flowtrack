# FlowTrack Web

Depuis ce dossier, avec Node.js 22.12+ et npm, exécuter `npm ci` puis `npm run dev`. Ouvrir http://localhost:5173. Démarrer PostgreSQL, Keycloak (8081) et l’API (8080) avant la connexion. Utiliser un compte du realm flowtrack membre d’un projet contenant des tickets.

Le client public flowtrack-frontend utilise le flux Authorization Code avec PKCE. Les jetons restent en mémoire. VITE_API_URL et VITE_KEYCLOAK_URL permettent de changer les origines. Autoriser également l’origine du frontend dans le client Keycloak et dans CORS côté API.

Tests : `npm test`. Compilation : `npm run build`.
