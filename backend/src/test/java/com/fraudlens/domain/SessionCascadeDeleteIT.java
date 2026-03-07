package com.fraudlens.domain;

import com.fraudlens.repository.EventRepository;
import com.fraudlens.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data integrity test: verifies that deleting a Session cascades to its Events
 * at both layers — JPA (CascadeType.ALL + orphanRemoval) and the database-level
 * ON DELETE CASCADE constraint defined in the Liquibase schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SessionCascadeDeleteIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    SessionRepository sessionRepository;

    @Autowired
    EventRepository eventRepository;

    @Test
    void deletingSession_cascadesDeleteToEvents() {
        // Given: a persisted session with two bound events
        Session session = Session.builder()
                .userId("u1").ip("1.1.1.1").country("US").device("web")
                .timestamp("2024-01-01T00:00:00Z").status(SessionStatus.SAFE)
                .build();

        Event e1 = Event.builder()
                .type(EventType.PAGE_VISIT).url("https://example.com")
                .durationMs(300L).build();
        Event e2 = Event.builder()
                .type(EventType.FORM_SUBMIT).url("https://example.com/checkout")
                .durationMs(1200L).build();

        session.addEvent(e1);
        session.addEvent(e2);
        sessionRepository.save(session);

        String sessionId = session.getId();
        assertThat(eventRepository.findBySessionId(sessionId)).hasSize(2);

        // When: the session is deleted
        sessionRepository.deleteById(sessionId);
        sessionRepository.flush();

        // Then: session is gone and all its events are gone with it
        assertThat(sessionRepository.findById(sessionId)).isEmpty();
        assertThat(eventRepository.findBySessionId(sessionId))
                .as("Events bound to the deleted session must be cascade-deleted")
                .isEmpty();
    }

    @Test
    void deletingSession_doesNotAffectEventsOfOtherSessions() {
        // Given: two sessions each with one event
        Session s1 = Session.builder()
                .userId("u1").ip("1.1.1.1").country("US").device("web")
                .timestamp("2024-01-01T00:00:00Z").status(SessionStatus.SAFE)
                .build();
        Session s2 = Session.builder()
                .userId("u2").ip("2.2.2.2").country("DE").device("mobile")
                .timestamp("2024-01-02T00:00:00Z").status(SessionStatus.SUSPICIOUS)
                .build();

        Event e1 = Event.builder()
                .type(EventType.LOGIN_ATTEMPT).url("https://example.com/login")
                .durationMs(500L).build();
        Event e2 = Event.builder()
                .type(EventType.PAGE_VISIT).url("https://example.com/dashboard")
                .durationMs(800L).build();

        s1.addEvent(e1);
        s2.addEvent(e2);
        sessionRepository.saveAll(List.of(s1, s2));

        // When: only s1 is deleted
        sessionRepository.deleteById(s1.getId());
        sessionRepository.flush();

        // Then: s2's event is untouched
        assertThat(eventRepository.findBySessionId(s2.getId()))
                .as("Events of a non-deleted session must remain intact")
                .hasSize(1);
    }
}
