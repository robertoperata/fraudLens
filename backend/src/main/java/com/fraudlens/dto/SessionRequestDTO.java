package com.fraudlens.dto;

import com.fraudlens.domain.SessionStatus;
import com.fraudlens.validation.ValidIpAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionRequestDTO {

    @NotBlank
    private String userId;

    @NotBlank
    @ValidIpAddress
    private String ip;

    @NotBlank
    @Pattern(
            regexp = "[A-Z]{2}",
            message = "must be a 2-letter ISO 3166-1 alpha-2 country code (e.g. IT, US)"
    )
    private String country;

    @NotBlank
    private String device;

    @NotBlank
    @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$",
            message = "must be ISO 8601 UTC format, e.g. 2028-11-02T10:20:11Z"
    )
    private String timestamp;

    // Optional — defaults to SAFE in the entity if omitted
    private SessionStatus status;
}
