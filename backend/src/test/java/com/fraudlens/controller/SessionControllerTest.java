package com.fraudlens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudlens.config.SecurityConfig;
import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import com.fraudlens.dto.RiskSummaryResponseDTO;
import com.fraudlens.dto.SessionRequestDTO;
import com.fraudlens.dto.SessionResponseDTO;
import com.fraudlens.dto.SessionSearchRequestDTO;
import com.fraudlens.exception.AIServiceException;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.repository.SessionRepository;
import com.fraudlens.security.JwtService;
import com.fraudlens.service.AIRiskSummaryService;
import com.fraudlens.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionController.class)
@Import(SecurityConfig.class)
class SessionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SessionService sessionService;
    @MockBean SessionRepository sessionRepository;
    @MockBean AIRiskSummaryService aiRiskSummaryService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SessionResponseDTO buildDTO(String id) {
        return SessionResponseDTO.builder().id(id).userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).riskScore(10).build();
    }

    private SessionRequestDTO buildRequest() {
        return new SessionRequestDTO("u1", "1.1.1.1", "US", "web",
                "2024-01-01T00:00:00Z", null);
    }

    // ── AC-AUTH-03: No token → 401 ────────────────────────────────────────────

    @Test
    void getAllSessions_noToken_returns401() throws Exception {
        mockMvc.perform(get("/sessions")).andExpect(status().isUnauthorized());
    }

    @Test
    void createSession_noToken_returns401() throws Exception {
        mockMvc.perform(post("/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }

    // ── AC-AUTH-04/05: Invalid JWT token → 401 ───────────────────────────────

    @Test
    void getAllSessions_malformedToken_returns401() throws Exception {
        when(jwtService.extractUsername(any())).thenThrow(new RuntimeException("bad token"));

        mockMvc.perform(get("/sessions")
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    // ── AC-SES-01: GET /sessions → 200 with riskScore ────────────────────────

    @Test
    @WithMockUser
    void getAllSessions_authenticated_returns200WithList() throws Exception {
        SessionResponseDTO dto = buildDTO("s1");
        when(sessionService.getAll()).thenReturn(List.of(dto));

        mockMvc.perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"))
                .andExpect(jsonPath("$[0].riskScore").value(10));
    }

    @Test
    @WithMockUser
    void getAllSessions_empty_returns200WithEmptyArray() throws Exception {
        when(sessionService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── AC-SES-05: GET /sessions/{id} → 200 with events + riskScore ──────────

    @Test
    @WithMockUser
    void getSessionById_found_returns200() throws Exception {
        SessionResponseDTO dto = buildDTO("s1");
        when(sessionService.getById("s1")).thenReturn(dto);

        mockMvc.perform(get("/sessions/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"))
                .andExpect(jsonPath("$.riskScore").exists());
    }

    // ── AC-SES-06: GET /sessions/{id} not found → 404 ────────────────────────

    @Test
    @WithMockUser
    void getSessionById_notFound_returns404() throws Exception {
        when(sessionService.getById("missing"))
                .thenThrow(new ResourceNotFoundException("Session not found: missing"));

        mockMvc.perform(get("/sessions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Session not found: missing"));
    }

    // ── AC-SES-02: POST /sessions → 201 ──────────────────────────────────────

    @Test
    @WithMockUser
    void createSession_validBody_returns201() throws Exception {
        SessionResponseDTO dto = buildDTO("s-new");
        when(sessionService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s-new"));
    }

    // ── AC-SES-03: POST /sessions missing required field → 400 ───────────────

    @Test
    @WithMockUser
    void createSession_missingUserId_returns400() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ip\":\"1.1.1.1\",\"country\":\"US\",\"device\":\"web\",\"timestamp\":\"2024-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    void createSession_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── AC-SES-04: POST /sessions invalid status → 400 ───────────────────────

    @Test
    @WithMockUser
    void createSession_invalidStatus_returns400() throws Exception {
        String body = "{\"userId\":\"u1\",\"ip\":\"1.1.1.1\",\"country\":\"US\"," +
                      "\"device\":\"web\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"status\":\"INVALID\"}";

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── AC-SES-07: PUT /sessions/{id} → 200 ──────────────────────────────────

    @Test
    @WithMockUser
    void updateSession_found_returns200() throws Exception {
        SessionResponseDTO dto = buildDTO("s1");
        when(sessionService.update(eq("s1"), any())).thenReturn(dto);

        mockMvc.perform(put("/sessions/s1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    @WithMockUser
    void updateSession_notFound_returns404() throws Exception {
        when(sessionService.update(eq("missing"), any()))
                .thenThrow(new ResourceNotFoundException("Session not found: missing"));

        mockMvc.perform(put("/sessions/missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound());
    }

    // ── AC-SES-08: DELETE /sessions/{id} → 204 ───────────────────────────────

    @Test
    @WithMockUser
    void deleteSession_found_returns204() throws Exception {
        mockMvc.perform(delete("/sessions/s1"))
                .andExpect(status().isNoContent());
    }

    // ── AC-SES-09: DELETE /sessions/{id} not found → 404 ─────────────────────

    @Test
    @WithMockUser
    void deleteSession_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Session not found: missing"))
                .when(sessionService).delete("missing");

        mockMvc.perform(delete("/sessions/missing"))
                .andExpect(status().isNotFound());
    }

    // ── AC-SRCH-01..05: POST /sessions/search → 200 ──────────────────────────

    @Test
    @WithMockUser
    void searchSessions_byStatus_returns200() throws Exception {
        SessionResponseDTO dto = buildDTO("s1");
        when(sessionService.search(any())).thenReturn(List.of(dto));

        mockMvc.perform(post("/sessions/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DANGEROUS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"));
    }

    @Test
    @WithMockUser
    void searchSessions_byCountry_returns200() throws Exception {
        when(sessionService.search(any())).thenReturn(List.of(buildDTO("s1")));

        mockMvc.perform(post("/sessions/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"IT\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void searchSessions_emptyBody_returns200WithAll() throws Exception {
        when(sessionService.search(any())).thenReturn(List.of(buildDTO("s1"), buildDTO("s2")));

        mockMvc.perform(post("/sessions/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser
    void searchSessions_withSort_returns200() throws Exception {
        when(sessionService.search(any())).thenReturn(List.of());

        mockMvc.perform(post("/sessions/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortBy\":\"timestamp\",\"sortDir\":\"desc\"}"))
                .andExpect(status().isOk());
    }

    // ── AC-AI-01: POST /sessions/{id}/risk-summary → 200 ─────────────────────

    @Test
    @WithMockUser
    void riskSummary_found_returns200WithSummary() throws Exception {
        Session session = Session.builder().id("s1").userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();

        when(sessionRepository.findByIdWithEvents("s1")).thenReturn(Optional.of(session));
        when(aiRiskSummaryService.generateRiskSummary(any(), any()))
                .thenReturn("Session appears legitimate. Risk level: LOW");

        mockMvc.perform(post("/sessions/s1/risk-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Session appears legitimate. Risk level: LOW"));
    }

    // ── AC-AI-02: POST /sessions/{id}/risk-summary not found → 404 ───────────

    @Test
    @WithMockUser
    void riskSummary_sessionNotFound_returns404() throws Exception {
        when(sessionRepository.findByIdWithEvents("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/sessions/missing/risk-summary"))
                .andExpect(status().isNotFound());
    }

    // ── AC-AI-03: POST /sessions/{id}/risk-summary AI unavailable → 503 ──────

    @Test
    @WithMockUser
    void riskSummary_aiUnavailable_returns503() throws Exception {
        Session session = Session.builder().id("s1").userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();

        when(sessionRepository.findByIdWithEvents("s1")).thenReturn(Optional.of(session));
        when(aiRiskSummaryService.generateRiskSummary(any(), any()))
                .thenThrow(new AIServiceException("AI service unavailable"));

        mockMvc.perform(post("/sessions/s1/risk-summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // ── Rate limit: POST /sessions/{id}/risk-summary → 429 after 10 calls ────

    @Test
    @WithMockUser(username = "rate-limit-test-user")
    void riskSummary_rateLimitExceeded_returns429() throws Exception {
        Session session = Session.builder().id("s-rl").userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();

        when(sessionRepository.findByIdWithEvents("s-rl")).thenReturn(Optional.of(session));
        when(aiRiskSummaryService.generateRiskSummary(any(), any())).thenReturn("ok");

        // Consume all 10 tokens
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/sessions/s-rl/risk-summary"))
                    .andExpect(status().isOk());
        }

        // 11th call must be rate-limited
        mockMvc.perform(post("/sessions/s-rl/risk-summary"))
                .andExpect(status().isTooManyRequests());
    }
}
