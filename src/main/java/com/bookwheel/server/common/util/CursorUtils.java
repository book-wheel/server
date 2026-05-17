package com.bookwheel.server.common.util;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class CursorUtils {

    private final ObjectMapper objectMapper;

    public CursorUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> String encode(T cursorObject) {
        if (cursorObject == null) {
            return null;
        }

        try {
            String json = objectMapper.writeValueAsString(cursorObject);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.CURSOR_ENCODING_ERROR);
        }
    }

    public <T> T decode(String cursorString, Class<T> clazz) {
        if (cursorString == null || cursorString.isBlank()) {
            return null;
        }

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursorString);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, clazz);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
