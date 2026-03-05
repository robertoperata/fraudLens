package com.fraudlens.dto;

import com.fraudlens.domain.SessionStatus;
import jakarta.validation.constraints.NotBlank;
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
    private String ip;

    @NotBlank
    private String country;

    @NotBlank
    private String device;

    @NotBlank
    private String timestamp;

    // Optional — defaults to SAFE in the entity if omitted
    private SessionStatus status;
}
