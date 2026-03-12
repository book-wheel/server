package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.bookwheel.server.common.util.SecurityUtil.getUserId;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티(갤러리) API", description = "게시물 업로드 및 사진 신고")
public class PostController {

    private final S3Service s3Service;
    private final PostService postService;

    @Operation(summary = "게시물 사진 업로드용 Presigned URL 다중 발급")
    @PostMapping("/{bookId}/images/presigned-urls")
    public ApiResponse<PostImagePresignedResponse> getPresignedUrls(
        @PathVariable("bookId") String bookId,
        @RequestBody PostImagePresignedRequest request) {
        PostImagePresignedResponse response = s3Service.getPostPresignedUrls(bookId, request.fileExtensions());
        return ApiResponse.success(response);
    }

    @Operation(summary = "게시물 업로드(사진 + 글)")
    @PostMapping("/{bookId}/save")
    public ApiResponse<PostCreateResponse> save(@PathVariable("bookId") String bookId,
                                                @RequestBody PostCreateRequest request, @AuthenticationPrincipal Object principal) {

        String userId = getUserId(principal);
        PostCreateResponse response = postService.create(bookId, request,userId);
        return ApiResponse.success(response);
    }


    @Operation(summary = "게시물 좋아요")
    @PostMapping("/{postId}/likes")
    public ApiResponse<String> togglePostLike(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal Object principal) {

        postService.togglePostLike(postId, getUserId(principal));
        return ApiResponse.success("공감 상태가 변경되었습니다.");
    }

    @Operation(summary = "게시물 댓글 작성")
    @PostMapping("/{postId}/comments")
    public ApiResponse<String> createPostComment(
        @PathVariable("postId") Long postId,
        @Valid @RequestBody PostCommentCreateRequest request,
        @AuthenticationPrincipal Object principal) {

        postService.createPostComment(postId, request, getUserId(principal));

        return ApiResponse.success("댓글이 성공적으로 작성되었습니다.");
    }


    @Operation(summary = "사진첩 신고")
    @PostMapping("/{postId}/reports")
    public ApiResponse<String> reportPhoto(@PathVariable("postId") Long photoId,@RequestBody PhotoReportRequest request) {
        // TODO: 신고 사유(DTO) 받아서 Service로 넘기기
        return ApiResponse.success(photoId +"신고처리 api 연결 성공");
    }
}
