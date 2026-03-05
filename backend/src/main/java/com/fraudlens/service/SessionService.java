package com.fraudlens.service;

import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import com.fraudlens.dto.SessionRequestDTO;
import com.fraudlens.dto.SessionResponseDTO;
import com.fraudlens.dto.SessionSearchRequestDTO;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.mapper.SessionMapper;
import com.fraudlens.repository.SessionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final RiskScoringService riskScoringService;

    @Transactional(readOnly = true)
    public List<SessionResponseDTO> getAll() {
        return sessionRepository.findAllWithEvents().stream()
                .map(this::toListDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionResponseDTO getById(String id) {
        Session session = sessionRepository.findByIdWithEvents(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        SessionResponseDTO dto = sessionMapper.toResponseDTO(session);
        dto.setRiskScore(riskScoringService.compute(session, session.getEvents()));
        return dto;
    }

    @Transactional
    public SessionResponseDTO create(SessionRequestDTO request) {
        Session session = sessionMapper.toEntity(request);
        // Apply the optional status from request (entity default is SAFE if null)
        if (request.getStatus() != null) {
            session.setStatus(request.getStatus());
        }
        Session saved = sessionRepository.save(session);
        return toListDTO(saved);
    }

    @Transactional
    public SessionResponseDTO update(String id, SessionRequestDTO request) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));

        session.setUserId(request.getUserId());
        session.setIp(request.getIp());
        session.setCountry(request.getCountry());
        session.setDevice(request.getDevice());
        session.setTimestamp(request.getTimestamp());
        if (request.getStatus() != null) {
            session.setStatus(request.getStatus());
        }

        Session saved = sessionRepository.save(session);
        return toListDTO(saved);
    }

    @Transactional
    public void delete(String id) {
        if (!sessionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Session not found: " + id);
        }
        sessionRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<SessionResponseDTO> search(SessionSearchRequestDTO request) {
        Specification<Session> spec = buildSpec(request);
        Sort sort = buildSort(request);
        return sessionRepository.findAll(spec, sort).stream()
                .map(this::toListDTO)
                .toList();
    }

    // --- helpers ---

    private SessionResponseDTO toListDTO(Session session) {
        SessionResponseDTO dto = sessionMapper.toListResponseDTO(session);
        dto.setRiskScore(riskScoringService.compute(session, session.getEvents()));
        return dto;
    }

    private Specification<Session> buildSpec(SessionSearchRequestDTO req) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (req.status() != null) {
                predicates.add(cb.equal(root.get("status"), req.status()));
            }
            if (req.country() != null && !req.country().isBlank()) {
                predicates.add(cb.equal(root.get("country"), req.country()));
            }
            if (req.userId() != null && !req.userId().isBlank()) {
                predicates.add(cb.equal(root.get("userId"), req.userId()));
            }
            if (req.ip() != null && !req.ip().isBlank()) {
                predicates.add(cb.equal(root.get("ip"), req.ip()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort buildSort(SessionSearchRequestDTO req) {
        String field     = req.sortBy()  != null ? req.sortBy()  : "timestamp";
        String direction = req.sortDir() != null ? req.sortDir() : "desc";
        return direction.equalsIgnoreCase("asc")
                ? Sort.by(field).ascending()
                : Sort.by(field).descending();
    }
}
