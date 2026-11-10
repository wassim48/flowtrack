package com.flowtrack.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtrack.projects.persistence.ProjectRepository;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class ProjectStreams {
    private record Subscription(long project, UUID user, Instant expires, SseEmitter emitter) {}
    private final Set<Subscription> clients = ConcurrentHashMap.newKeySet();
    private final ProjectRepository projects;
    private final ObjectMapper json;

    public ProjectStreams(ProjectRepository projects, ObjectMapper json) {
        this.projects = projects;
        this.json = json;
    }

    @GetMapping(path="/api/events/projects/{project}", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter open(@PathVariable long project, @AuthenticationPrincipal Jwt jwt) throws IOException {
        UUID user = UUID.fromString(jwt.getSubject());
        if (projects.findVisibleBoardRevision(project,user).isEmpty())
            throw new AccessDeniedException("Project membership required");
        Instant expires = jwt.getExpiresAt();
        long lifetime = Math.min(60000, Duration.between(Instant.now(),expires).toMillis());
        if (lifetime <= 0) throw new AccessDeniedException("Expired session");
        var emitter = new SseEmitter(lifetime);
        var sub = new Subscription(project,user,expires,emitter);
        clients.add(sub);
        emitter.onCompletion(() -> clients.remove(sub));
        emitter.onTimeout(() -> { clients.remove(sub); emitter.complete(); });
        emitter.onError(error -> clients.remove(sub));
        try {
            emitter.send(SseEmitter.event().name("ready").data("1"));
        } catch (IOException | IllegalStateException failure) {
            clients.remove(sub);
            throw failure;
        }
        return emitter;
    }

    // Each API instance broadcasts to its own connected browsers.
    @KafkaListener(topics="flowtrack.issue.events",groupId="flowtrack-live-${random.uuid}",
            autoStartup="${flowtrack.live.enabled:true}",properties="auto.offset.reset=latest")
    public void changed(String raw) throws Exception {
        long project = Long.parseLong(json.readTree(raw).required("projectId").asText());
        for (var sub : clients) {
            if (sub.project()!=project) continue;
            if (!Instant.now().isBefore(sub.expires()) || projects.findVisibleBoardRevision(project,sub.user()).isEmpty()) {
                clients.remove(sub);
                sub.emitter().complete();
                continue;
            }
            try {
                sub.emitter().send(SseEmitter.event().name("board").data("1"));
            } catch (IOException | IllegalStateException closed) {
                clients.remove(sub);
            }
        }
    }
}
