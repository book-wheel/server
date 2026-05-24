package com.bookwheel.server.common.util;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.common.cursor.InterestCursor;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorUtilsTest {

    private CursorUtils cursorUtils;

    @BeforeEach
    void setUp() {
        cursorUtils = new CursorUtils(new ObjectMapper().findAndRegisterModules());
    }

    @Test
    @DisplayName("encode and decode gallery cursor")
    void encodeAndDecodeGalleryCursor() {
        GalleryCursor cursor = new GalleryCursor(LocalDateTime.of(2026, 5, 17, 10, 15, 30), 10L);

        String encodedCursor = cursorUtils.encode(cursor);
        GalleryCursor decodedCursor = cursorUtils.decode(encodedCursor, GalleryCursor.class);

        assertEquals(cursor, decodedCursor);
        assertFalse(encodedCursor.contains("+"));
        assertFalse(encodedCursor.contains("/"));
    }

    @Test
    @DisplayName("encode and decode interest cursor")
    void encodeAndDecodeInterestCursor() {
        InterestCursor cursor = new InterestCursor(LocalDateTime.of(2026, 5, 17, 10, 15, 30), 20L);

        String encodedCursor = cursorUtils.encode(cursor);
        InterestCursor decodedCursor = cursorUtils.decode(encodedCursor, InterestCursor.class);

        assertEquals(cursor, decodedCursor);
    }

    @Test
    @DisplayName("decode null or blank cursor as first page")
    void decodeNullOrBlankCursor() {
        assertNull(cursorUtils.decode(null, GalleryCursor.class));
        assertNull(cursorUtils.decode("", GalleryCursor.class));
        assertNull(cursorUtils.decode("   ", GalleryCursor.class));
    }

    @Test
    @DisplayName("throw INVALID_CURSOR for invalid Base64 cursor")
    void decodeInvalidBase64Cursor() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> cursorUtils.decode("not-a-valid-cursor!", GalleryCursor.class)
        );

        assertEquals(ErrorCode.INVALID_CURSOR, exception.getErrorCode());
    }
}
