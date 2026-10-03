package com.bookwheel.server.common.oauth2.apple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AppleTokenRevocationClientTest {

    @Test
    @DisplayName("Apple revoke endpoint에 refresh token과 동적 client secret을 전송한다")
    void revokesRefreshToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator generator = mock(AppleClientSecretGenerator.class);
        given(generator.generate("client-id")).willReturn("client-secret");
        AppleTokenRevocationClient client = new AppleTokenRevocationClient(
                builder.build(), generator, "client-id"
        );
        server.expect(requestTo("https://appleid.apple.com/auth/revoke"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(
                        "client_id=client-id&client_secret=client-secret&token=refresh-token"
                                + "&token_type_hint=refresh_token"
                ))
                .andRespond(withSuccess());

        client.revokeRefreshToken("refresh-token");

        server.verify();
    }
}
