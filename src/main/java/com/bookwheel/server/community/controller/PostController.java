package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.ApiResponse;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.dto.*;
import com.bookwheel.server.community.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import static com.bookwheel.server.common.util.SecurityUtil.getUserPK;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Tag(name = "도서 및 커뮤니티(갤러리) API", description = "게시물 업로드 및 사진 신고")
public class PostController {

    private final S3Service s3Service;
    private final PostService postService;

    @Operation(summary = "게시물 사진 업로드용 Presigned URL 다중 발급")
    @PostMapping("/{isbn}/images/presigned-urls")
    public ApiResponse<PostImagePresignedResponse> getPresignedUrls(
        @PathVariable("isbn") String isbn,
        @RequestBody PostImagePresignedRequest request) {
        PostImagePresignedResponse response = s3Service.getPostPresignedUrls(isbn, request.fileExtensions());
        return ApiResponse.success(response);
    }

    @Operation(summary = "게시물 업로드(사진 + 글)")
    @PostMapping("/{isbn}/save")
    public ApiResponse<PostCreateResponse> save(@Valid @RequestBody PostCreateRequest request,
                                                @AuthenticationPrincipal Object principal) {

        String userPK = getUserPK(principal);
        PostCreateResponse response = postService.create(request,userPK);
        return ApiResponse.success(response);
    }


    @Operation(summary = "게시물 상세 조회", description = "게시물 상세 정보(작성자, 이미지, 좋아요/댓글 수, 내 좋아요 여부 등)를 조회합니다.")
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getPostDetail(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal Object principal) {

        PostDetailResponse response = postService.getPostDetail(postId, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "게시물 좋아요")
    @PostMapping("/{postId}/likes")
    public ApiResponse<String> togglePostLike(
        @PathVariable("postId") Long postId,
        @AuthenticationPrincipal Object principal) {

        postService.togglePostLike(postId, getUserPK(principal));
        return ApiResponse.success("공감 상태가 변경되었습니다.");
    }

    @Operation(summary = "게시물 댓글 목록 조회", description = "게시물에 달린 댓글을 최신순 커서 페이징으로 조회합니다. 내가 작성한 댓글 여부(isMine)를 포함합니다.")
    @GetMapping("/{postId}/comments")
    public ApiResponse<CursorPageResponse<PostCommentResponse>> getPostComments(
        @PathVariable("postId") Long postId,
        @Parameter(description = "다음 페이지 조회용 커서")
        @RequestParam(required = false) String cursor,
        @Parameter(description = "한 번에 조회할 댓글 개수", example = "20")
        @RequestParam(required = false, defaultValue = "20") Integer size,
        @AuthenticationPrincipal Object principal) {

        CursorPageResponse<PostCommentResponse> response =
            postService.getPostComments(postId, cursor, size, getUserPK(principal));
        return ApiResponse.success(response);
    }

    @Operation(summary = "게시물 댓글 작성")
    @PostMapping("/{postId}/comments")
    public ApiResponse<String> createPostComment(
        @PathVariable("postId") Long postId,
        @Valid @RequestBody PostCommentCreateRequest request,
        @AuthenticationPrincipal Object principal) {

        postService.createPostComment(postId, request, getUserPK(principal));

        return ApiResponse.success("댓글이 성공적으로 작성되었습니다.");
    }


    @Operation(summary = "게시물 신고")
    @PostMapping("/{postId}/reports")
    public ApiResponse<String> reportPost(
        @PathVariable("postId") Long postId,
        @Valid @RequestBody PostReportRequest request,
        @AuthenticationPrincipal Object principal) {
        postService.reportPost(postId, request, getUserPK(principal));
        return ApiResponse.success("게시물이 성공적으로 신고 접수되었습니다.");
    }
}
