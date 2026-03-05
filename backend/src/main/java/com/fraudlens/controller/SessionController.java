package com.fraudlens.controller;

import com.fraudlens.dto.RiskSummaryResponseDTO;
import com.fraudlens.dto.SessionRequestDTO;
import com.fraudlens.dto.SessionResponseDTO;
import com.fraudlens.dto.SessionSearchRequestDTO;
import com.fraudlens.exception.ResourceNotFoundException;
import com.fraudlens.repository.SessionRepository;
import com.fraudlens.service.AIRiskSummaryService;
import com.fraudlens.service.SessionService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;
    private final SessionRepository sessionRepository;
    private final AIRiskSummaryService aiRiskSummaryService;

    // Per-user buckets: 10 AI requests per minute
    private final Map<String, Bucket> rateLimitBuckets = new ConcurrentHashMap<>();

    @GetMapping
    @Operation(summary = "List all sessions with risk scores")
    @ApiResponse(responseCode = "200", description = "Sessions returned")
    public ResponseEntity<List<SessionResponseDTO>> getAll() {
        return ResponseEntity.ok(sessionService.getAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a session with its events and risk score")
    @ApiResponse(responseCode = "200", description = "Session found")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<SessionResponseDTO> getById(@PathVariable String id) {
        return ResponseEntity.ok(sessionService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new session")
    @ApiResponse(responseCode = "201", description = "Session created")
    @ApiResponse(responseCode = "400", description = "Validation error")
    public ResponseEntity<SessionResponseDTO> create(@RequestBody @Valid SessionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing session")
    @ApiResponse(responseCode = "200", description = "Session updated")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<SessionResponseDTO> update(
            @PathVariable String id,
            @RequestBody @Valid SessionRequestDTO request) {
        return ResponseEntity.ok(sessionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a session and all its events")
    @ApiResponse(responseCode = "204", description = "Session deleted")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sessionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/search")
    @Operation(summary = "Filter and sort sessions")
    @ApiResponse(responseCode = "200", description = "Filtered sessions returned")
    public ResponseEntity<List<SessionResponseDTO>> search(
            @RequestBody @Valid SessionSearchRequestDTO request) {
        return ResponseEntity.ok(sessionService.search(request));
    }

    @PostMapping("/{id}/risk-summary")
    @Operation(summary = "Generate an AI natural language risk summary for a session")
    @ApiResponse(responseCode = "200", description = "Summary generated")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    @ApiResponse(responseCode = "503", description = "AI service unavailable")
    public ResponseEntity<RiskSummaryResponseDTO> generateRiskSummary(
            @PathVariable String id,
            Authentication auth) {

        Bucket bucket = rateLimitBuckets.computeIfAbsent(auth.getName(), k ->
                Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(10)
                                .refillGreedy(10, Duration.ofMinutes(1))
                                .build())
                        .build());

        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        var session = sessionRepository.findByIdWithEvents(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));

        String summary = aiRiskSummaryService.generateRiskSummary(session, session.getEvents());
        return ResponseEntity.ok(new RiskSummaryResponseDTO(summary));
    }

}
