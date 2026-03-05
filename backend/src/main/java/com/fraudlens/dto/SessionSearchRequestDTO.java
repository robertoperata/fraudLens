package com.fraudlens.dto;

import com.fraudlens.domain.SessionStatus;
import jakarta.validation.constraints.Pattern;

public record SessionSearchRequestDTO(
        SessionStatus status,
        String country,
        String userId,
        String ip,
        @Pattern(regexp = "timestamp|id") String sortBy,
        @Pattern(regexp = "asc|desc")     String sortDir
) {}
