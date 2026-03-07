package com.fraudlens.dto;

import com.fraudlens.domain.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponseDTO {

    private String id;
    private String sessionId;   // mapped from event.session.id via MapStruct
    private EventType type;
    private String url;
    private Long durationMs;
    private String metadata;
    private Instant createdAt;
}
