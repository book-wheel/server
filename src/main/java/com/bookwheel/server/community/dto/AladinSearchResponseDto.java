package com.bookwheel.server.community.dto;

import java.util.List;

public record AladinSearchResponseDto(
    List<AladinItemDto> item
) {}
