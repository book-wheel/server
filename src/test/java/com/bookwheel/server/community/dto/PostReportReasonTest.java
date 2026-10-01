package com.bookwheel.server.community.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class PostReportReasonTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(PostReportReason.class)
    void acceptsEveryDocumentedReason(PostReportReason reason) throws Exception {
        String body = "{\"reason\":\"" + reason.name() + "\"}";
        assertThat(objectMapper.readValue(body, PostReportRequest.class).reason()).isEqualTo(reason);
        assertThat(objectMapper.readValue(body, PostCommentReportRequest.class).reason()).isEqualTo(reason);
        assertThat(objectMapper.writeValueAsString(reason)).isEqualTo("\"" + reason.name() + "\"");
    }
}
