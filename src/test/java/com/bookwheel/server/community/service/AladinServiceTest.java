package com.bookwheel.server.community.service;

import com.bookwheel.server.community.dto.BookDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AladinServiceTest {

    @Test
    void getBookDetailByIsbn_RequestsBigCoverImage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AladinService aladinService = new AladinService(builder.build());
        ReflectionTestUtils.setField(aladinService, "aladinApiKey", "test-key");
        ReflectionTestUtils.setField(aladinService, "aladinApiUrl", "https://aladin.example.com/search");

        String isbn = "9780132350884";
        String responseBody = """
            {
              "item": [
                {
                  "title": "Clean Code",
                  "author": "Robert C. Martin",
                  "publisher": "Prentice Hall",
                  "description": "A handbook of agile software craftsmanship.",
                  "cover": "https://image.aladin.co.kr/product/big-cover.jpg",
                  "isbn13": "9780132350884",
                  "subInfo": {
                    "itemPage": 464,
                    "toc": "Contents"
                  }
                }
              ]
            }
            """;

        server.expect(once(), requestTo(allOf(
                containsString("ItemId=" + isbn),
                containsString("Cover=Big")
        ))).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        BookDetailResponse response = aladinService.getBookDetailByIsbn(isbn, true);

        assertThat(response.cover()).isEqualTo("https://image.aladin.co.kr/product/big-cover.jpg");
        assertThat(response.isInterested()).isTrue();
        server.verify();
    }
}
