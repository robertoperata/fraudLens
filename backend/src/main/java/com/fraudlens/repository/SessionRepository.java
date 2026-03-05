package com.fraudlens.repository;

import com.fraudlens.domain.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String>, JpaSpecificationExecutor<Session> {

    @Query("SELECT DISTINCT s FROM Session s LEFT JOIN FETCH s.events")
    List<Session> findAllWithEvents();

    @Query("SELECT s FROM Session s LEFT JOIN FETCH s.events WHERE s.id = :id")
    Optional<Session> findByIdWithEvents(@Param("id") String id);
}
