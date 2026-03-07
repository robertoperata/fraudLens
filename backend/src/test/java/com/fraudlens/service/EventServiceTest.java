package com.fraudlens.service;

import com.fraudlens.domain.Event;
import com.fraudlens.domain.EventType;
import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import com.fraudlens.dto.EventRequestDTO;
import com.fraudlens.dto.EventResponseDTO;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.mapper.EventMapper;
import com.fraudlens.repository.EventRepository;
import com.fraudlens.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository eventRepository;
    @Mock SessionRepository sessionRepository;
    @Mock EventMapper eventMapper;
    @InjectMocks EventService eventService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Session buildSession(String id) {
        return Session.builder().id(id).userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();
    }

    private Event buildEvent(String id, Session session) {
        return Event.builder().id(id).type(EventType.PAGE_VISIT)
                .url("https://example.com").durationMs(1000L).session(session).build();
    }

    // ── getEventsForSession ───────────────────────────────────────────────────

    @Test
    void getEventsForSession_sessionExists_returnsMappedList() {
        Session s = buildSession("s1");
        Event e = buildEvent("e1", s);
        EventResponseDTO dto = new EventResponseDTO("e1", "s1", EventType.PAGE_VISIT,
                "https://example.com", 1000L, null, null);

        when(sessionRepository.existsById("s1")).thenReturn(true);
        when(eventRepository.findBySessionIdOrderByCreatedAtDesc("s1")).thenReturn(List.of(e));
        when(eventMapper.toResponseDTOList(List.of(e))).thenReturn(List.of(dto));

        List<EventResponseDTO> result = eventService.getEventsForSession("s1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionId()).isEqualTo("s1");
    }

    @Test
    void getEventsForSession_sessionNotFound_throws() {
        when(sessionRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> eventService.getEventsForSession("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── addEvent ──────────────────────────────────────────────────────────────

    @Test
    void addEvent_sessionExists_savesAndReturnsDTO() {
        Session s = buildSession("s1");
        EventRequestDTO req = new EventRequestDTO(EventType.FORM_SUBMIT,
                "https://example.com/form", 2000L, "{\"key\":\"val\"}");
        EventResponseDTO dto = new EventResponseDTO("e-new", "s1", EventType.FORM_SUBMIT,
                "https://example.com/form", 2000L, "{\"key\":\"val\"}", null);

        when(sessionRepository.findById("s1")).thenReturn(Optional.of(s));
        when(sessionRepository.save(any(Session.class))).thenReturn(s);
        when(eventRepository.findByIdAndSessionId(any(), eq("s1"))).thenReturn(Optional.empty());
        when(eventMapper.toResponseDTO(any())).thenReturn(dto);

        EventResponseDTO result = eventService.addEvent("s1", req);

        assertThat(result.getSessionId()).isEqualTo("s1");
        verify(sessionRepository).save(s);
    }

    @Test
    void addEvent_sessionNotFound_throws() {
        when(sessionRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.addEvent("missing",
                new EventRequestDTO(EventType.PAGE_VISIT, "https://example.com", 1000L, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    // ── deleteEvent ───────────────────────────────────────────────────────────

    @Test
    void deleteEvent_found_deletesEvent() {
        Session s = buildSession("s1");
        Event e = buildEvent("e1", s);

        when(sessionRepository.existsById("s1")).thenReturn(true);
        when(eventRepository.findByIdAndSessionId("e1", "s1")).thenReturn(Optional.of(e));

        eventService.deleteEvent("s1", "e1");

        verify(eventRepository).delete(e);
    }

    @Test
    void deleteEvent_sessionNotFound_throws() {
        when(sessionRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> eventService.deleteEvent("missing", "e1"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(eventRepository, never()).delete(any());
    }

    @Test
    void deleteEvent_eventNotFound_throws() {
        when(sessionRepository.existsById("s1")).thenReturn(true);
        when(eventRepository.findByIdAndSessionId("missing-event", "s1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.deleteEvent("s1", "missing-event"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing-event");
    }

    @Test
    void deleteEvent_eventBelongsToDifferentSession_throws() {
        // findByIdAndSessionId returns empty when event belongs to a different session
        when(sessionRepository.existsById("s1")).thenReturn(true);
        when(eventRepository.findByIdAndSessionId("e-other", "s1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.deleteEvent("s1", "e-other"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
