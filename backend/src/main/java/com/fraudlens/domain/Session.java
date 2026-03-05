package com.fraudlens.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String ip;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String device;

    @Column(nullable = false)
    private String timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.SAFE;

    // Cascade ALL + orphanRemoval = application-layer guarantee for cascade delete.
    // DB-level ON DELETE CASCADE in 001-create-schema.sql is the safety-net fallback.
    @OneToMany(
            mappedBy = "session",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<Event> events = new ArrayList<>();

    @PrePersist
    private void generateId() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
    }

    // Convenience methods — always manage both sides of the bidirectional relationship.
    public void addEvent(Event event) {
        events.add(event);
        event.setSession(this);
    }

    public void removeEvent(Event event) {
        events.remove(event);
        event.setSession(null);
    }
}
