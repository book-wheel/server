package com.bookwheel.server.book.controller;

import com.bookwheel.server.book.dto.OwnBookRegisterRequest;
import com.bookwheel.server.book.dto.OwnBookRegisterResponse;
import com.bookwheel.server.book.service.GroupBookService;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupBookController.class)
class GroupBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupBookService groupBookService;

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
    @DisplayName("Group-Inner: register own book success")
    void registerOwnBook_Success() throws Exception {
        String groupId = "group-123";
        OwnBookRegisterRequest request = new OwnBookRegisterRequest(
                "9791190090018",
                "Test Book",
                "Test Author",
                "Test Publisher",
                LocalDate.of(2020, 1, 1),
                "https://example.com/cover.jpg",
                250,
                "Good",
                "Please handle with care"
        );

        OwnBookRegisterResponse response = new OwnBookRegisterResponse("own-1");
        given(groupBookService.registerOwnBook(eq(groupId), any(OwnBookRegisterRequest.class), any()))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/groups/{groupId}/books", groupId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownBookId").value("own-1"));
    }

    @Test
    @WithMockUser
    @DisplayName("Group-Inner: register own book validation error")
    void registerOwnBook_ValidationError() throws Exception {
        String groupId = "group-123";
        OwnBookRegisterRequest request = new OwnBookRegisterRequest(
                "",
                "Test Book",
                "Test Author",
                "Test Publisher",
                LocalDate.of(2020, 1, 1),
                "https://example.com/cover.jpg",
                250,
                "Good",
                "Please handle with care"
        );

        mockMvc.perform(post("/api/v1/groups/{groupId}/books", groupId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
