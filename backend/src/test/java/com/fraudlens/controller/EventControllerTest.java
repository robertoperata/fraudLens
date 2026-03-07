package com.fraudlens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraudlens.config.SecurityConfig;
import com.fraudlens.domain.EventType;
import com.fraudlens.dto.EventRequestDTO;
import com.fraudlens.dto.EventResponseDTO;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.security.JwtService;
import com.fraudlens.service.EventService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
@Import(SecurityConfig.class)
class EventControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean EventService eventService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private EventResponseDTO buildDTO(String id, String sessionId) {
        return new EventResponseDTO(id, sessionId, EventType.PAGE_VISIT,
                "https://example.com", 1000L, null, null);
    }

    private EventRequestDTO buildRequest() {
        return new EventRequestDTO(EventType.PAGE_VISIT, "https://example.com", 1000L, null);
    }

    // ── No auth → 401 ─────────────────────────────────────────────────────────

    @Test
    void getEvents_noToken_returns401() throws Exception {
        mockMvc.perform(get("/sessions/s1/events")).andExpect(status().isUnauthorized());
    }

    // ── AC-EVT-01: GET /sessions/{id}/events → 200 ────────────────────────────

    @Test
    @WithMockUser
    void getEvents_sessionExists_returns200WithList() throws Exception {
        EventResponseDTO dto = buildDTO("e1", "s1");
        when(eventService.getEventsForSession("s1")).thenReturn(List.of(dto));

        mockMvc.perform(get("/sessions/s1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("e1"))
                .andExpect(jsonPath("$[0].sessionId").value("s1"))
                .andExpect(jsonPath("$[0].type").value("PAGE_VISIT"));
    }

    @Test
    @WithMockUser
    void getEvents_sessionNotFound_returns404() throws Exception {
        when(eventService.getEventsForSession("missing"))
                .thenThrow(new ResourceNotFoundException("Session not found: missing"));

        mockMvc.perform(get("/sessions/missing/events"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── AC-EVT-02: POST /sessions/{id}/events → 201 ───────────────────────────

    @Test
    @WithMockUser
    void addEvent_validBody_returns201() throws Exception {
        EventResponseDTO dto = buildDTO("e-new", "s1");
        when(eventService.addEvent(eq("s1"), any())).thenReturn(dto);

        mockMvc.perform(post("/sessions/s1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("e-new"))
                .andExpect(jsonPath("$.sessionId").value("s1"));
    }

    // ── AC-EVT-02 validation: missing required field → 400 ────────────────────

    @Test
    @WithMockUser
    void addEvent_missingType_returns400() throws Exception {
        mockMvc.perform(post("/sessions/s1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"durationMs\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser
    void addEvent_missingUrl_returns400() throws Exception {
        mockMvc.perform(post("/sessions/s1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PAGE_VISIT\",\"durationMs\":1000}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void addEvent_missingDurationMs_returns400() throws Exception {
        mockMvc.perform(post("/sessions/s1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PAGE_VISIT\",\"url\":\"https://example.com\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── AC-EVT-03: POST /sessions/{id}/events session not found → 404 ─────────

    @Test
    @WithMockUser
    void addEvent_sessionNotFound_returns404() throws Exception {
        when(eventService.addEvent(eq("missing"), any()))
                .thenThrow(new ResourceNotFoundException("Session not found: missing"));

        mockMvc.perform(post("/sessions/missing/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isNotFound());
    }

    // ── AC-EVT-04: DELETE /sessions/{id}/events/{eventId} → 204 ──────────────

    @Test
    @WithMockUser
    void deleteEvent_found_returns204() throws Exception {
        mockMvc.perform(delete("/sessions/s1/events/e1"))
                .andExpect(status().isNoContent());
    }

    // ── AC-EVT-05: DELETE — event not found → 404 ─────────────────────────────

    @Test
    @WithMockUser
    void deleteEvent_eventNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Event not found: missing-event"))
                .when(eventService).deleteEvent("s1", "missing-event");

        mockMvc.perform(delete("/sessions/s1/events/missing-event"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Event not found: missing-event"));
    }

    // ── AC-EVT-06: DELETE — eventId from different session → 404 ─────────────

    @Test
    @WithMockUser
    void deleteEvent_wrongSession_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Event not found: e-other"))
                .when(eventService).deleteEvent("s1", "e-other");

        mockMvc.perform(delete("/sessions/s1/events/e-other"))
                .andExpect(status().isNotFound());
    }
}
