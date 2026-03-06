package com.fraudlens.service;

import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import com.fraudlens.dto.SessionRequestDTO;
import com.fraudlens.dto.SessionResponseDTO;
import com.fraudlens.dto.SessionSearchRequestDTO;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.mapper.SessionMapper;
import com.fraudlens.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock SessionMapper sessionMapper;
    @Mock RiskScoringService riskScoringService;
    @InjectMocks SessionService sessionService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Session buildSession(String id) {
        return Session.builder().id(id).userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();
    }

    private SessionResponseDTO buildResponseDTO(String id) {
        return SessionResponseDTO.builder().id(id).userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_returnsMappedList() {
        Session s1 = buildSession("s1");
        Session s2 = buildSession("s2");
        SessionResponseDTO d1 = buildResponseDTO("s1");
        SessionResponseDTO d2 = buildResponseDTO("s2");

        when(sessionRepository.findAllWithEvents()).thenReturn(List.of(s1, s2));
        when(sessionMapper.toListResponseDTO(s1)).thenReturn(d1);
        when(sessionMapper.toListResponseDTO(s2)).thenReturn(d2);
        when(riskScoringService.compute(any(), any())).thenReturn(0);

        List<SessionResponseDTO> result = sessionService.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("s1");
        assertThat(result.get(1).getId()).isEqualTo("s2");
    }

    @Test
    void getAll_setsRiskScore() {
        Session s = buildSession("s1");
        SessionResponseDTO dto = buildResponseDTO("s1");

        when(sessionRepository.findAllWithEvents()).thenReturn(List.of(s));
        when(sessionMapper.toListResponseDTO(s)).thenReturn(dto);
        when(riskScoringService.compute(s, s.getEvents())).thenReturn(42);

        List<SessionResponseDTO> result = sessionService.getAll();

        assertThat(result.get(0).getRiskScore()).isEqualTo(42);
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsDTO() {
        Session s = buildSession("s1");
        SessionResponseDTO dto = buildResponseDTO("s1");

        when(sessionRepository.findByIdWithEvents("s1")).thenReturn(Optional.of(s));
        when(sessionMapper.toResponseDTO(s)).thenReturn(dto);
        when(riskScoringService.compute(s, s.getEvents())).thenReturn(30);

        SessionResponseDTO result = sessionService.getById("s1");

        assertThat(result.getId()).isEqualTo("s1");
        assertThat(result.getRiskScore()).isEqualTo(30);
    }

    @Test
    void getById_notFound_throws() {
        when(sessionRepository.findByIdWithEvents("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getById("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesAndReturnsDTO() {
        SessionRequestDTO req = new SessionRequestDTO("u1", "1.1.1.1", "US", "web",
                "2024-01-01T00:00:00Z", null);
        Session entity = buildSession("s1");
        Session saved = buildSession("s1");
        SessionResponseDTO dto = buildResponseDTO("s1");

        when(sessionMapper.toEntity(req)).thenReturn(entity);
        when(sessionRepository.save(entity)).thenReturn(saved);
        when(sessionMapper.toListResponseDTO(saved)).thenReturn(dto);
        when(riskScoringService.compute(any(), any())).thenReturn(0);

        SessionResponseDTO result = sessionService.create(req);

        assertThat(result.getId()).isEqualTo("s1");
        verify(sessionRepository).save(entity);
    }

    @Test
    void create_withStatus_setsStatus() {
        SessionRequestDTO req = new SessionRequestDTO("u1", "1.1.1.1", "US", "web",
                "2024-01-01T00:00:00Z", SessionStatus.DANGEROUS);
        Session entity = buildSession("s1");
        Session saved = buildSession("s1");
        SessionResponseDTO dto = buildResponseDTO("s1");

        when(sessionMapper.toEntity(req)).thenReturn(entity);
        when(sessionRepository.save(entity)).thenReturn(saved);
        when(sessionMapper.toListResponseDTO(saved)).thenReturn(dto);
        when(riskScoringService.compute(any(), any())).thenReturn(0);

        sessionService.create(req);

        assertThat(entity.getStatus()).isEqualTo(SessionStatus.DANGEROUS);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_found_updatesFields() {
        Session existing = buildSession("s1");
        SessionRequestDTO req = new SessionRequestDTO("u2", "2.2.2.2", "DE", "mobile",
                "2024-06-01T00:00:00Z", SessionStatus.SUSPICIOUS);
        Session saved = buildSession("s1");
        SessionResponseDTO dto = buildResponseDTO("s1");

        when(sessionRepository.findById("s1")).thenReturn(Optional.of(existing));
        when(sessionRepository.save(existing)).thenReturn(saved);
        when(sessionMapper.toListResponseDTO(saved)).thenReturn(dto);
        when(riskScoringService.compute(any(), any())).thenReturn(0);

        sessionService.update("s1", req);

        assertThat(existing.getUserId()).isEqualTo("u2");
        assertThat(existing.getIp()).isEqualTo("2.2.2.2");
        assertThat(existing.getCountry()).isEqualTo("DE");
        assertThat(existing.getDevice()).isEqualTo("mobile");
        assertThat(existing.getStatus()).isEqualTo(SessionStatus.SUSPICIOUS);
    }

    @Test
    void update_notFound_throws() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.update("missing",
                new SessionRequestDTO("u", "ip", "c", "d", "ts", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existing_callsDeleteById() {
        when(sessionRepository.existsById("s1")).thenReturn(true);

        sessionService.delete("s1");

        verify(sessionRepository).deleteById("s1");
    }

    @Test
    void delete_notFound_throws() {
        when(sessionRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> sessionService.delete("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(sessionRepository, never()).deleteById(any());
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void search_withStatus_returnsMappedList() {
        Session s = buildSession("s1");
        SessionResponseDTO dto = buildResponseDTO("s1");
        SessionSearchRequestDTO req = new SessionSearchRequestDTO(
                SessionStatus.DANGEROUS, null, null, null, null, null);

        when(sessionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(s));
        when(sessionMapper.toListResponseDTO(s)).thenReturn(dto);
        when(riskScoringService.compute(any(), any())).thenReturn(0);

        List<SessionResponseDTO> result = sessionService.search(req);

        assertThat(result).hasSize(1);
    }

    @Test
    void search_emptyRequest_usesDefaultSort() {
        SessionSearchRequestDTO req = new SessionSearchRequestDTO(
                null, null, null, null, null, null);

        when(sessionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of());

        List<SessionResponseDTO> result = sessionService.search(req);

        assertThat(result).isEmpty();
        verify(sessionRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    void search_withSortAsc_sortsAscending() {
        SessionSearchRequestDTO req = new SessionSearchRequestDTO(
                null, null, null, null, "timestamp", "asc");

        when(sessionRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of());

        sessionService.search(req);

        verify(sessionRepository).findAll(any(Specification.class),
                eq(Sort.by("timestamp").ascending()));
    }
}
