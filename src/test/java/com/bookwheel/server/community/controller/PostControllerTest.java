package com.bookwheel.server.community.controller;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.dto.PhotoReportRequest;
import com.bookwheel.server.community.dto.PostCommentCreateRequest;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCreateResponse;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
        String bookId = "book-1";
        PostImagePresignedRequest request = new PostImagePresignedRequest(List.of("jpg"));
        PostImagePresignedResponse response = new PostImagePresignedResponse(
                List.of(new PostImagePresignedResponse.PresignedInfo(
                        "https://example.com/presigned",
                        "posts/1/abc.jpg"
                ))
        );

        given(s3Service.getPostPresignedUrls(eq(bookId), any(List.class))).willReturn(response);

        mockMvc.perform(post("/api/v1/posts/{bookId}/images/presigned-urls", bookId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presignedUrls[0].objectKey").value("posts/1/abc.jpg"));
    }

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: save post success")
    void savePost_Success() throws Exception {
        String bookId = "book-1";
        PostCreateRequest request = new PostCreateRequest(
                "Nice book",
                List.of("posts/1/abc.jpg")
        );
        PostCreateResponse response = new PostCreateResponse(
                10L,
                "book-1",
                "Nice book",
                List.of("posts/1/abc.jpg")
        );

        given(postService.create(eq(bookId), any(PostCreateRequest.class), any()))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/posts/{bookId}/save", bookId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postId").value(10L))
                .andExpect(jsonPath("$.data.bookId").value("book-1"));
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

    @Test
    @WithMockUser
    @DisplayName("Community Gallery: report photo success")
    void reportPhoto_Success() throws Exception {
        Long photoId = 99L;

        mockMvc.perform(post("/api/v1/posts/{postId}/reports", photoId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isString());
    }
}



