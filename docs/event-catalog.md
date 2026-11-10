# Événements Kafka de FlowTrack

Depuis infrastructure, lancer `docker compose up -d kafka`. Le broker de développement est publié sur localhost:19092. L’API accepte la variable KAFKA_BOOTSTRAP_SERVERS.

Les événements de ticket utilisent le topic flowtrack.issue.events : ISSUE_CREATED, ISSUE_UPDATED, ISSUE_ASSIGNED, ISSUE_STATUS_CHANGED et ISSUE_DELETED. Une désassignation a un newAssigneeId nul. Les commentaires produisent COMMENT_CREATED et, pour chaque membre mentionné, COMMENT_MENTIONED sur flowtrack.comment.events. Chaque message contient eventId, schemaVersion, occurredAt, aggregateType, aggregateId, projectId, actorId et data. La clé Kafka est le projectId.

La modification métier et la ligne outbox sont enregistrées dans une transaction PostgreSQL. Le publisher envoie les lignes disponibles et les marque publiées après acquittement. Le délai après erreur augmente jusqu’à 60 secondes. Les lignes publiées sont nettoyées après sept jours. Un arrêt entre acquittement et commit peut republier le même eventId : les futurs consommateurs doivent le dédupliquer. L’ordre Kafka est propre à une partition d’un topic.

OutboxIntegrationTest vérifie le rollback, suspend le broker de test durant une assignation, le relance et lit les messages dans Kafka. Le test navigateur utilise un broker Kafka temporaire sur le port 29092 et vérifie les activités visibles par Bob.
