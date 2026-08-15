package com.bookwheel.server.community.service;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.BookDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AladinServiceTest {

    @Test
    void getBookDetailByIsbn_RequestsBigCoverImage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AladinService aladinService = aladinService(builder.build());

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
                containsString("itemIdType=ISBN13"),
                containsString("Cover=Big")
        ))).andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        BookDetailResponse response = aladinService.getBookDetailByIsbn(isbn, true);

        assertThat(response.cover()).isEqualTo("https://image.aladin.co.kr/product/big-cover.jpg");
        assertThat(response.isInterested()).isTrue();
        server.verify();
    }

    @Test
    void getBookDetailByIsbn_UsesIsbnItemIdTypeForTenDigitIsbn() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AladinService aladinService = aladinService(builder.build());

        String isbn = "8937460440";
        server.expect(once(), requestTo(allOf(
                containsString("ItemId=" + isbn),
                containsString("itemIdType=ISBN")
        ))).andRespond(withSuccess(responseBody("9788937460449"), MediaType.APPLICATION_JSON));

        BookDetailResponse response = aladinService.getBookDetailByIsbn(isbn, true);

        assertThat(response.isbn()).isEqualTo("9788937460449");
        server.verify();
    }

    @Test
    void getBookDetailByIsbn_ThrowsAladinApiErrorOnErrorResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AladinService aladinService = aladinService(builder.build());

        String isbn = "9780132350884";
        server.expect(once(), requestTo(containsString("ItemId=" + isbn)))
            .andRespond(withServerError());

        assertThatThrownBy(() -> aladinService.getBookDetailByIsbn(isbn, true))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.ALADIN_API_ERROR);
        server.verify();
    }

    @Test
    void getBookDetailByIsbn_ThrowsAladinApiErrorOnUnsupportedContentType() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AladinService aladinService = aladinService(builder.build());

        String isbn = "9780132350884";
        server.expect(once(), requestTo(containsString("ItemId=" + isbn)))
            .andRespond(withSuccess(responseBody(isbn), MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> aladinService.getBookDetailByIsbn(isbn, true))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(ErrorCode.ALADIN_API_ERROR);
        server.verify();
    }

    private AladinService aladinService(RestClient restClient) {
        AladinService aladinService = new AladinService(restClient);
        ReflectionTestUtils.setField(aladinService, "aladinApiKey", "test-key");
        ReflectionTestUtils.setField(aladinService, "aladinApiUrl", "https://aladin.example.com/search");
        return aladinService;
    }

    private String responseBody(String isbn13) {
        return """
            {
              "item": [
                {
                  "title": "Clean Code",
                  "author": "Robert C. Martin",
                  "publisher": "Prentice Hall",
                  "description": "A handbook of agile software craftsmanship.",
                  "cover": "https://image.aladin.co.kr/product/big-cover.jpg",
                  "isbn13": "%s",
                  "subInfo": {
                    "itemPage": 464,
                    "toc": "Contents"
                  }
                }
              ]
            }
            """.formatted(isbn13);
    }
}
