package com.bookwheel.server.common.util;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;



public class CursorUtils {

    private final ObjectMapper objectMapper;

    public CursorUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    //객체 -> Base64 문자열(인코딩)
    public <T> String encode(T cursorObject){
        if(cursorObject == null) return null;
        try{
            String json = objectMapper.writeValueAsString(cursorObject);
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }catch(JsonProcessingException e){
            throw new BusinessException(ErrorCode.CURSOR_ENCODING_ERROR);
        }
    }

    //Base64 문자열 -> 객체(디코딩)
    public <T> T decode(String cursorString, Class<T> clazz){// 자바 제네릭(Type Erasure) 다시 공부하기!! 다까먹음
        if(cursorString == null || cursorString.isBlank()) return null; //isblank는 공백까지 true로 반환한다는걸 알았음
        try {
            byte[] bytes = Base64.getDecoder().decode(cursorString);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, clazz);
        }catch (JsonProcessingException e){
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
