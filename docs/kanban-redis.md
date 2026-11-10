# Kanban et Redis (phase 3)

Depuis `infrastructure`, lancer `docker compose up -d redis`. L’API utilise
`localhost:16379` par défaut. Les variables `REDIS_HOST` et `REDIS_PORT`
permettent de changer cette adresse.

- `GET /api/projects/{projectId}/board` renvoie les tickets groupés par statut.
- `GET /api/projects/{projectId}/board/counters` renvoie les quatre effectifs,
  y compris les statuts vides. Une lecture à froid utilise un GROUP BY PostgreSQL.
- Chaque requête vérifie en base l’appartenance au projet avant de lire Redis.
  Un trigger change la révision du tableau dans la transaction d’écriture.
  Les nouvelles lectures utilisent cette révision dans leur clé. Les anciennes
  entrées expirent après 60 secondes. Un rollback conserve la révision précédente.
- En cas d’indisponibilité de Redis, le tableau et les compteurs sont lus en
  PostgreSQL. Les compteurs Micrometer `flowtrack.board.cache` utilisent le tag
  `result` (hit, miss, unavailable, write_error ou invalid).
- La création est limitée à 30 requêtes par utilisateur sur une fenêtre de
  60 secondes ouverte par sa première tentative autorisée. La propriété
  `flowtrack.issue-creates-per-minute` règle ce seuil. Un dépassement renvoie
  HTTP 429. Si Redis est indisponible, la création reste possible et la métrique
  `flowtrack.issue.create.limit.unavailable` enregistre le contournement.

La suppression est réservée à l’administrateur du projet et exige la version
affichée du ticket. Une version périmée renvoie HTTP 409.

Les tests de `IssueControllerIntegrationTest` lancent PostgreSQL et Redis
avec Testcontainers, puis vérifient notamment la réutilisation d’une entrée,
le contrôle d’accès, les changements de révision et le dépassement de la limite.
