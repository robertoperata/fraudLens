package com.fraudlens.mapper;

import com.fraudlens.domain.Session;
import com.fraudlens.dto.SessionRequestDTO;
import com.fraudlens.dto.SessionResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {EventMapper.class})
public interface SessionMapper {

    // Full mapping including nested events — used for GET /sessions/{id}
    @Mapping(target = "riskScore", ignore = true)
    SessionResponseDTO toResponseDTO(Session session);

    // List/search mapping — events omitted, riskScore set manually in service
    @Mapping(target = "riskScore", ignore = true)
    @Mapping(target = "events", ignore = true)
    SessionResponseDTO toListResponseDTO(Session session);

    // DTO → entity for create; id and events are managed separately
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "events", ignore = true)
    Session toEntity(SessionRequestDTO dto);
}
