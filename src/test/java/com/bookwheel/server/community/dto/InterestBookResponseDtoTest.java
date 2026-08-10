package com.bookwheel.server.community.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterestBookResponseDtoTest {

    @Test
    @DisplayName("저장된 도서 정보가 없어도 제목·저자·표지를 null로 내려보내지 않는다.")
    void normalizesMissingBookDetails() {
        InterestBookResponseDto dto = new InterestBookResponseDto(
            1L, "9791165341909", null, null, null, LocalDateTime.now());

        assertThat(dto.title()).isEqualTo("제목 없음");
        assertThat(dto.author()).isEqualTo("저자 미상");
        assertThat(dto.coverImageUrl()).isEmpty();
    }

    @Test
    @DisplayName("공백만 있는 값도 저장된 도서 정보가 없는 것으로 본다.")
    void normalizesBlankBookDetails() {
        InterestBookResponseDto dto = new InterestBookResponseDto(
            1L, "9791165341909", "  ", "  ", "  ", LocalDateTime.now());

        assertThat(dto.title()).isEqualTo("제목 없음");
        assertThat(dto.author()).isEqualTo("저자 미상");
        assertThat(dto.coverImageUrl()).isEmpty();
    }

    @Test
    @DisplayName("저장된 도서 정보가 있으면 그대로 내려준다.")
    void keepsStoredBookDetails() {
        InterestBookResponseDto dto = new InterestBookResponseDto(
            1L, "9791165341909", "달러구트 꿈 백화점", "이미예",
            "https://image.aladin.co.kr/cover.jpg", LocalDateTime.now());

        assertThat(dto.title()).isEqualTo("달러구트 꿈 백화점");
        assertThat(dto.author()).isEqualTo("이미예");
        assertThat(dto.coverImageUrl()).isEqualTo("https://image.aladin.co.kr/cover.jpg");
    }
}
