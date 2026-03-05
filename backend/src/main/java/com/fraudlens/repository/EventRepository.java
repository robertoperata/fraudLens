package com.fraudlens.repository;

import com.fraudlens.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findBySessionId(String sessionId);

    Optional<Event> findByIdAndSessionId(String id, String sessionId);

    boolean existsBySessionId(String sessionId);
}
