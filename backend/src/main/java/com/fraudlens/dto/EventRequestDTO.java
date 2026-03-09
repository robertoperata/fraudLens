package com.fraudlens.dto;

import com.fraudlens.domain.EventType;
import com.fraudlens.validation.ValidJson;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(
            regexp = "^/[^\\s]*$",
            message = "must be an absolute URL path starting with / (e.g. /checkout/payment)"
    )
    private String url;

    @NotNull
    @Min(value = 0, message = "must be zero or greater")
    private Long durationMs;

    // Optional — must be valid JSON when provided
    @ValidJson
    private String metadata;
}
