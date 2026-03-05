package com.fraudlens.service;

import com.fraudlens.domain.Event;
import com.fraudlens.domain.Session;
import com.fraudlens.dto.EventRequestDTO;
import com.fraudlens.dto.EventResponseDTO;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.mapper.EventMapper;
import com.fraudlens.repository.EventRepository;
import com.fraudlens.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final EventMapper eventMapper;

    @Transactional(readOnly = true)
    public List<EventResponseDTO> getEventsForSession(String sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }
        return eventMapper.toResponseDTOList(eventRepository.findBySessionId(sessionId));
    }

    @Transactional
    public EventResponseDTO addEvent(String sessionId, EventRequestDTO request) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        Event event = Event.builder()
                .type(request.getType())
                .url(request.getUrl())
                .durationMs(request.getDurationMs())
                .metadata(request.getMetadata())
                .build();

        session.addEvent(event);
        sessionRepository.save(session);

        // Re-fetch to get the generated id after persist
        Event saved = eventRepository.findByIdAndSessionId(event.getId(), sessionId)
                .orElse(event);
        return eventMapper.toResponseDTO(saved);
    }

    @Transactional
    public void deleteEvent(String sessionId, String eventId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("Session not found: " + sessionId);
        }
        Event event = eventRepository.findByIdAndSessionId(eventId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        eventRepository.delete(event);
    }
}
