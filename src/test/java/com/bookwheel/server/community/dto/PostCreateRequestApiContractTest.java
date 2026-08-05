package com.bookwheel.server.community.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PostCreateRequestApiContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("게시물 작성 요청 본문은 ISBN 필드를 노출하지 않는다")
    void createRequest_DoesNotExposeIsbnField() {
        List<String> fieldNames = Arrays.stream(PostCreateRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        PostCreateRequest request = new PostCreateRequest(
                "Clean Code",
                "post content",
                List.of("posts/9780132350884/image.heic"),
                "group-1"
        );

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(fieldNames).containsExactly("title", "content", "objectKeys", "groupId");
        assertThat(json.has("isbn")).isFalse();
        assertThat(json.get("title").asText()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("게시물 작성 요청 본문은 ISBN 없이 역직렬화된다")
    void createRequest_DeserializesWithoutIsbn() throws Exception {
        PostCreateRequest request = objectMapper.readValue(
                """
                        {
                          "title": "Clean Code",
                          "content": "post content",
                          "objectKeys": ["posts/9780132350884/image.heic"],
                          "groupId": "group-1"
                        }
                        """,
                PostCreateRequest.class
        );

        assertThat(request.title()).isEqualTo("Clean Code");
        assertThat(request.objectKeys()).containsExactly("posts/9780132350884/image.heic");
        assertThat(request.groupId()).isEqualTo("group-1");
    }
}
