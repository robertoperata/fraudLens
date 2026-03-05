package com.fraudlens.dto;

import com.fraudlens.domain.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventRequestDTO {

    @NotNull
    private EventType type;

    @NotBlank
    private String url;

    @NotNull
    private Long durationMs;

    // Nullable — free-form JSON text
    private String metadata;
}
