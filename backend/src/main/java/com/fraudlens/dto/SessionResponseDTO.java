package com.fraudlens.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fraudlens.domain.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponseDTO {

    private String id;
    private String userId;
    private String ip;
    private String country;
    private String device;
    private String timestamp;
    private SessionStatus status;
    private int riskScore;

    // Omitted from JSON when null (list/search endpoints do not include events)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<EventResponseDTO> events;
}
