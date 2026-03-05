package com.fraudlens.controller;

import com.fraudlens.dto.EventRequestDTO;
import com.fraudlens.dto.EventResponseDTO;
import com.fraudlens.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessions/{sessionId}/events")
@RequiredArgsConstructor
@Tag(name = "Events")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "List all events for a session")
    @ApiResponse(responseCode = "200", description = "Events returned")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<List<EventResponseDTO>> getAll(@PathVariable String sessionId) {
        return ResponseEntity.ok(eventService.getEventsForSession(sessionId));
    }

    @PostMapping
    @Operation(summary = "Add an event to a session")
    @ApiResponse(responseCode = "201", description = "Event created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<EventResponseDTO> create(
            @PathVariable String sessionId,
            @RequestBody @Valid EventRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.addEvent(sessionId, request));
    }

    @DeleteMapping("/{eventId}")
    @Operation(summary = "Delete a single event")
    @ApiResponse(responseCode = "204", description = "Event deleted")
    @ApiResponse(responseCode = "404", description = "Event not found or does not belong to this session")
    public ResponseEntity<Void> delete(
            @PathVariable String sessionId,
            @PathVariable String eventId) {
        eventService.deleteEvent(sessionId, eventId);
        return ResponseEntity.noContent().build();
    }
}
