package com.fraudlens.mapper;

import com.fraudlens.domain.Event;
import com.fraudlens.dto.EventResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventMapper {

    // sessionId is not a direct field on Event — navigate via session.id
    @Mapping(source = "session.id", target = "sessionId")
    EventResponseDTO toResponseDTO(Event event);

    List<EventResponseDTO> toResponseDTOList(List<Event> events);
}
