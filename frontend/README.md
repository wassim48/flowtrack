# FlowTrack Web

Depuis ce dossier, avec Node.js 22.12+ et npm, exécuter `npm ci` puis `npm run dev`. Ouvrir http://localhost:5173. Démarrer PostgreSQL, Keycloak (8081) et l’API (8080) avant la connexion. Utiliser un compte du realm flowtrack membre d’un projet contenant des tickets.

Le client public flowtrack-frontend utilise le flux Authorization Code avec PKCE. Les jetons restent en mémoire. VITE_API_URL et VITE_KEYCLOAK_URL permettent de changer les origines. Autoriser également l’origine du frontend dans le client Keycloak et dans CORS côté API.

Tests : `npm test`. Compilation : `npm run build`.

## Test navigateur Alice / Bob

Depuis `frontend`, avec Docker Desktop, Java 21 et Node.js disponibles :

    npm ci
    npx playwright install chromium
    npm run test:e2e

Pour utiliser Microsoft Edge déjà installé sous Windows, lancer
`PLAYWRIGHT_CHANNEL=msedge npm run test:e2e` depuis Git Bash.

Le scénario démarre trois conteneurs temporaires (PostgreSQL, Keycloak et Kafka),
une API et un frontend de test. Garder libres les ports 15173, 18080,
18081, 25432 et 29092. Les comptes et le mot de passe sont générés pour cet essai.
Alice crée un projet, ajoute Bob et lui assigne un ticket. Bob se connecte
dans une session distincte, déplace ce ticket et Alice vérifie le nouveau
statut après actualisation.
