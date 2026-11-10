package com.flowtrack.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtrack.issues.application.IssueService;
import com.flowtrack.issues.domain.IssueType;
import com.flowtrack.projects.application.ProjectService;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class OutboxIntegrationTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static KafkaContainer broker = new KafkaContainer("apache/kafka:3.9.1");

    @DynamicPropertySource
    static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("flowtrack.activity.enabled", () -> true);
        r.add("flowtrack.notifications.enabled", () -> true);
        r.add("spring.kafka.bootstrap-servers", broker::getBootstrapServers);
    }

    @Autowired ProjectService projects;
    @Autowired IssueService issues;
    @Autowired JdbcTemplate db;
    @Autowired TransactionTemplate tx;
    @Autowired OutboxPublisher publisher;
    @Autowired ObjectMapper json;
    @Autowired com.flowtrack.activity.ActivityConsumer activityConsumer;
    @Autowired com.flowtrack.activity.ActivityController activity;
    @Autowired com.flowtrack.notifications.NotificationInbox inbox;
    @Autowired com.flowtrack.notifications.NotificationConsumer consumer;

    @Test
    void commitsAssignmentWhileBrokerIsDownAndDeliversSameIdsAfterRecovery() throws Exception {
        var alice = Jwt.withTokenValue("test").header("alg","none").subject(UUID.randomUUID().toString())
                .claim("email","alice@example.test").claim("preferred_username","alice").build();
        var project = projects.createProject("BOX","Outbox",null,alice);
        var projectId = project.getId();
        var userId = project.getCreatedBy().getId();
        tx.executeWithoutResult(status -> {
            issues.createIssue(projectId,"Discard",null,IssueType.TASK,null,userId,alice);
            status.setRollbackOnly();
        });
        assertEquals(0, db.queryForObject("select count(*) from outbox_events", Integer.class));

        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(broker.getContainerId()).exec();
        try {
            issues.createIssue(projectId,"Deliver",null,IssueType.TASK,null,userId,alice);
            assertFalse(publisher.publishNext());
            assertEquals(2,db.queryForObject("select count(*) from outbox_events where published_at is null",Integer.class));
        } finally {
            docker.unpauseContainerCmd(broker.getContainerId()).exec();
        }
        var ids = db.queryForList("select event_id::text from outbox_events order by seq",String.class);
        var deadline = System.nanoTime()+Duration.ofSeconds(50).toNanos();
        while (System.nanoTime()<deadline && db.queryForObject("select count(*) from outbox_events where published_at is null",Integer.class)>0) {
            if (!publisher.publishNext()) Thread.sleep(200);
        }
        assertEquals(2,db.queryForObject("select count(*) from outbox_events where published_at is not null",Integer.class));
        var props = Map.<String,Object>of("bootstrap.servers",broker.getBootstrapServers(),"group.id","verify-"+UUID.randomUUID(),
                "auto.offset.reset","earliest","key.deserializer",StringDeserializer.class,"value.deserializer",StringDeserializer.class);
        try (var reader = new KafkaConsumer<String,String>(props)) {
            reader.subscribe(List.of(IssueEvents.TOPIC));
            var seen = new LinkedHashMap<String,String>();
            deadline=System.nanoTime()+Duration.ofSeconds(20).toNanos();
            while (seen.size()<2 && System.nanoTime()<deadline) {
                for (var record : reader.poll(Duration.ofMillis(300))) {
                    assertEquals(projectId.toString(), record.key());
                    var event = json.readTree(record.value());
                    seen.put(event.get("eventId").asText(),event.get("eventType").asText());
                    if ("ISSUE_ASSIGNED".equals(event.get("eventType").asText()))
                        assertEquals(userId.longValue(), event.at("/data/newAssigneeId").asLong());
                }
            }
            UUID subject = UUID.fromString(alice.getSubject());
            deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (inbox.page(subject,Long.MAX_VALUE).unread()==0 && System.nanoTime()<deadline) Thread.sleep(200);
            var page = inbox.page(subject,Long.MAX_VALUE);
            assertEquals(1,page.unread());
            assertEquals("BOX-1",page.items().get(0).issueKey());
            long notificationId = page.items().get(0).id();
            UUID outsider = UUID.randomUUID();
            assertTrue(inbox.page(outsider,Long.MAX_VALUE).items().isEmpty());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,()->inbox.markRead(outsider,notificationId));
            inbox.markRead(subject,notificationId);
            String replay = db.queryForObject("select envelope::text from outbox_events where event_id=?::uuid",String.class,ids.get(1));
            consumer.receive(replay);
            deadline = System.nanoTime()+Duration.ofSeconds(20).toNanos();
            while(activity.list(projectId,1,null,alice).items().size()<2 && System.nanoTime()<deadline) Thread.sleep(200);
            activityConsumer.receive(replay);
            var history=activity.list(projectId,1,null,alice).items();
            assertEquals(List.of("ISSUE_ASSIGNED","ISSUE_CREATED"),history.stream().map(e->e.kind()).toList());
            assertEquals("alice",history.get(0).target());
            assertEquals(1,activity.list(projectId,1,history.get(0).id(),alice).items().size());
            var stranger=Jwt.withTokenValue("test").header("alg","none").subject(UUID.randomUUID().toString())
                    .claim("email","unknown@example.test").build();
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    ()->activity.list(projectId,1,null,stranger));
            var after = inbox.page(subject,Long.MAX_VALUE);
            assertEquals(1,after.items().size());
            assertTrue(after.items().get(0).read());
            assertEquals(0,after.unread());
            assertEquals(ids,List.copyOf(seen.keySet()));
            assertEquals(List.of("ISSUE_CREATED","ISSUE_ASSIGNED"),List.copyOf(seen.values()));
        }
    }
}
