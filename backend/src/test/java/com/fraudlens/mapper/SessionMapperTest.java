package com.fraudlens.mapper;

import com.fraudlens.domain.Event;
import com.fraudlens.domain.EventType;
import com.fraudlens.domain.Session;
import com.fraudlens.domain.SessionStatus;
import com.fraudlens.dto.EventResponseDTO;
import com.fraudlens.dto.SessionRequestDTO;
import com.fraudlens.dto.SessionResponseDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {SessionMapperImpl.class, EventMapperImpl.class})
class SessionMapperTest {

    @Autowired SessionMapper sessionMapper;
    @Autowired EventMapper eventMapper;

    // ── toResponseDTO (with nested events) ───────────────────────────────────

    @Test
    void toResponseDTO_mapsAllScalarFields() {
        Session session = buildSessionWithEvent();

        SessionResponseDTO dto = sessionMapper.toResponseDTO(session);

        assertThat(dto.getId()).isEqualTo("s1");
        assertThat(dto.getUserId()).isEqualTo("u1");
        assertThat(dto.getIp()).isEqualTo("1.2.3.4");
        assertThat(dto.getCountry()).isEqualTo("US");
        assertThat(dto.getDevice()).isEqualTo("desktop");
        assertThat(dto.getTimestamp()).isEqualTo("2024-06-01T12:00:00Z");
        assertThat(dto.getStatus()).isEqualTo(SessionStatus.SAFE);
    }

    @Test
    void toResponseDTO_mapsNestedEvents() {
        Session session = buildSessionWithEvent();

        SessionResponseDTO dto = sessionMapper.toResponseDTO(session);

        assertThat(dto.getEvents()).isNotNull().hasSize(1);
        EventResponseDTO eventDTO = dto.getEvents().get(0);
        assertThat(eventDTO.getId()).isEqualTo("e1");
        assertThat(eventDTO.getSessionId()).isEqualTo("s1");
        assertThat(eventDTO.getType()).isEqualTo(EventType.PAGE_VISIT);
        assertThat(eventDTO.getUrl()).isEqualTo("https://example.com");
        assertThat(eventDTO.getDurationMs()).isEqualTo(1500L);
    }

    @Test
    void toResponseDTO_riskScoreIgnored_remainsDefault() {
        Session session = buildSessionWithEvent();
        SessionResponseDTO dto = sessionMapper.toResponseDTO(session);
        // riskScore is set by service after mapping; mapper leaves it at default (0)
        assertThat(dto.getRiskScore()).isZero();
    }

    // ── toListResponseDTO (no nested events) ─────────────────────────────────

    @Test
    void toListResponseDTO_omitsEvents() {
        Session session = buildSessionWithEvent();

        SessionResponseDTO dto = sessionMapper.toListResponseDTO(session);

        assertThat(dto.getEvents()).isNull();
    }

    @Test
    void toListResponseDTO_mapsScalarFields() {
        Session session = buildSessionWithEvent();

        SessionResponseDTO dto = sessionMapper.toListResponseDTO(session);

        assertThat(dto.getId()).isEqualTo("s1");
        assertThat(dto.getUserId()).isEqualTo("u1");
        assertThat(dto.getStatus()).isEqualTo(SessionStatus.SAFE);
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Test
    void toEntity_mapsScalarFields() {
        SessionRequestDTO dto = new SessionRequestDTO("u2", "5.6.7.8", "DE",
                "mobile", "2024-07-01T08:00:00Z", SessionStatus.SUSPICIOUS);

        Session entity = sessionMapper.toEntity(dto);

        assertThat(entity.getUserId()).isEqualTo("u2");
        assertThat(entity.getIp()).isEqualTo("5.6.7.8");
        assertThat(entity.getCountry()).isEqualTo("DE");
        assertThat(entity.getDevice()).isEqualTo("mobile");
        assertThat(entity.getTimestamp()).isEqualTo("2024-07-01T08:00:00Z");
        assertThat(entity.getStatus()).isEqualTo(SessionStatus.SUSPICIOUS);
    }

    @Test
    void toEntity_idIsNull() {
        SessionRequestDTO dto = new SessionRequestDTO("u2", "5.6.7.8", "DE",
                "mobile", "2024-07-01T08:00:00Z", null);

        Session entity = sessionMapper.toEntity(dto);

        assertThat(entity.getId()).isNull();
    }

    @Test
    void toEntity_eventsIsEmpty() {
        SessionRequestDTO dto = new SessionRequestDTO("u2", "5.6.7.8", "DE",
                "mobile", "2024-07-01T08:00:00Z", null);

        Session entity = sessionMapper.toEntity(dto);

        assertThat(entity.getEvents()).isEmpty();
    }

    // ── EventMapper.toResponseDTO ─────────────────────────────────────────────

    @Test
    void eventMapper_toResponseDTO_mapsSessionId() {
        Session session = Session.builder().id("s1").userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();
        Event event = Event.builder().id("e1").session(session)
                .type(EventType.LOGIN_ATTEMPT).url("https://example.com/login")
                .durationMs(3000L).metadata(null).build();

        EventResponseDTO dto = eventMapper.toResponseDTO(event);

        assertThat(dto.getSessionId()).isEqualTo("s1");
        assertThat(dto.getId()).isEqualTo("e1");
        assertThat(dto.getType()).isEqualTo(EventType.LOGIN_ATTEMPT);
    }

    @Test
    void eventMapper_toResponseDTOList_mapsAll() {
        Session session = Session.builder().id("s1").userId("u1").ip("1.1.1.1")
                .country("US").device("web").timestamp("2024-01-01T00:00:00Z")
                .status(SessionStatus.SAFE).build();
        List<Event> events = List.of(
                Event.builder().id("e1").session(session).type(EventType.PAGE_VISIT)
                        .url("https://a.com").durationMs(1000L).build(),
                Event.builder().id("e2").session(session).type(EventType.FORM_SUBMIT)
                        .url("https://b.com").durationMs(2000L).build()
        );

        List<EventResponseDTO> dtos = eventMapper.toResponseDTOList(events);

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getId()).isEqualTo("e1");
        assertThat(dtos.get(1).getId()).isEqualTo("e2");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Session buildSessionWithEvent() {
        Session session = Session.builder()
                .id("s1").userId("u1").ip("1.2.3.4").country("US")
                .device("desktop").timestamp("2024-06-01T12:00:00Z")
                .status(SessionStatus.SAFE).build();

        Event event = Event.builder()
                .id("e1").session(session).type(EventType.PAGE_VISIT)
                .url("https://example.com").durationMs(1500L).metadata(null).build();

        session.getEvents().add(event);
        return session;
    }
}
