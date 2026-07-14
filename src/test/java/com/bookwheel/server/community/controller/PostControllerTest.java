package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.dto.PostCommentCreateRequest;
import com.bookwheel.server.community.dto.PostCommentResponse;
import com.bookwheel.server.community.dto.PostDetailResponse;
import com.bookwheel.server.community.dto.PostImagePresignedRequest;
import com.bookwheel.server.community.dto.PostImagePresignedResponse;
import com.bookwheel.server.community.service.PostService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private S3Service s3Service;

    @MockitoBean
    private PostService postService;

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("SUCCESS: " + context.getDisplayName());
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            System.out.println("FAIL: " + context.getDisplayName());
            System.out.println("이유: " + cause.getMessage());
        }
    };

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: get presigned urls success")
    void getPresignedUrls_Success() throws Exception {
        String isbn = "9788966263158";
        PostImagePresignedRequest request = new PostImagePresignedRequest(List.of("jpg"));
        PostImagePresignedResponse response = new PostImagePresignedResponse(
                List.of(new PostImagePresignedResponse.PresignedInfo(
                        "https://example.com/presigned",
                        "posts/1/abc.jpg"
                ))
        );

        given(s3Service.getPostPresignedUrls(eq(isbn), any(List.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/posts/{isbn}/images/presigned-urls", isbn)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrls[0].objectKey").value("posts/1/abc.jpg"));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: get post detail success")
    void getPostDetail_Success() throws Exception {
        Long postId = 10L;
        PostDetailResponse response = new PostDetailResponse(
                postId,
                "9791161571188",
                "문소희",
                "https://cdn.example.com/profile.png",
                "소카모임", // groupName (모임에서 작성한 글)
                "내 남편을 팝니다",
                "게시글 내용",
                List.of("https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"),
                26,
                3L,
                true,
                LocalDateTime.of(2026, 6, 23, 12, 0)
        );
        given(postService.getPostDetail(eq(postId), any())).willReturn(response);

        mockMvc.perform(get("/api/v1/posts/{postId}", postId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(10L))
                .andExpect(jsonPath("$.data.author").value("문소희"))
                .andExpect(jsonPath("$.data.groupName").value("소카모임"))
                .andExpect(jsonPath("$.data.title").value("내 남편을 팝니다"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.commentCount").value(3))
                .andExpect(jsonPath("$.data.isLikedByMe").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: get post comments success")
    void getPostComments_Success() throws Exception {
        Long postId = 10L;
        PostCommentResponse comment = new PostCommentResponse(
                1L,
                postId,
                "문소희",
                "https://cdn.example.com/profile.png",
                "댓글 내용",
                true,
                LocalDateTime.of(2026, 6, 23, 12, 0)
        );
        CursorPageResponse<PostCommentResponse> page =
                CursorPageResponse.of(List.of(comment), 20, 1L, false, null);
        given(postService.getPostComments(eq(postId), any(), any(), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].commentId").value(1L))
                .andExpect(jsonPath("$.data.content[0].isMine").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: toggle like success")
    void togglePostLike_Success() throws Exception {
        Long postId = 7L;

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: create comment success")
    void createPostComment_Success() throws Exception {
        Long postId = 7L;
        PostCommentCreateRequest request = new PostCommentCreateRequest("Great post");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: create comment validation error")
    void createPostComment_ValidationError() throws Exception {
        Long postId = 7L;
        PostCommentCreateRequest request = new PostCommentCreateRequest("");

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

}


