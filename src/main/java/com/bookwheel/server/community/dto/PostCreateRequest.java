package com.bookwheel.server.community.dto;

import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "도서 게시물(감상평+사진) 최종 등록 요청")
public record PostCreateRequest(

    @Schema(description = "알라딘 도서 고유 식별자(ISBN)", example = "9788966263158")
    @NotBlank(message = "ISBN은 필수입니다.")
    String isbn,

    @Schema(
        description = "도서 제목. 도서 검색 결과의 제목을 그대로 전달한다.",
        example = "클린 코드",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "도서 제목은 필수입니다.")
    @Size(max = 255, message = "도서 제목은 255자를 넘을 수 없습니다.")
    String title,

    @Schema(description = "감상평 내용", example = "이 페이지 진짜 너무 웃김 ㅋㅋㅋ")
    String content,

    @Schema(description = "S3에 업로드 완료된 이미지 객체 키 목록 (최대 5개)", example = "[\"posts/105/abcd_image.jpg\"]")
    @Size(max = 5, message = "사진은 최대 5장까지만 업로드할 수 있습니다.")
    List<String> objectKeys,

    @Schema(description = "작성한 모임 ID (모임 화면에서 작성 시 전달, 개인 작성이면 생략)", nullable = true)
    String groupId
) {

    public Post toEntity(BookInfo bookInfo, User uploader, Group group, String bookTitle) {
        return Post.builder()
            .bookInfo(bookInfo)
            .bookTitle(bookTitle)
            .uploader(uploader)
            .group(group)
            .content(this.content)
            .build();
    }
}
