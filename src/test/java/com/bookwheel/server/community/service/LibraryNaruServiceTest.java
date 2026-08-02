package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bookwheel.server.community.dto.LibraryNaruUsageAnalysisResponse;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 도서관정보나루 호출 및 실패 처리 테스트.
 * 실제 인증키를 사용하지 않고 더미 값과 가짜 서버 응답으로 검증한다.
 */
class LibraryNaruServiceTest {

    private static final String API_URL = "https://data4library.kr/api/usageAnalysisList";
    private static final String ISBN = "9788954681179";

    // 실제 응답과 동일한 구조. 매핑하지 않는 항목(request, loanHistory, gender, ranking 등)도 함께 담아
    // 사용하지 않는 필드가 있어도 파싱에 실패하지 않는지 같이 확인한다.
    private static final String SUCCESS_BODY = """
        {
          "response": {
            "request": {"isbn13": "9788954681179"},
            "book": {"bookname": "밝은 밤", "publisher": "문학동네", "loanCnt": 104490},
            "loanHistory": [{"loan": {"month": "2026-07", "loanCnt": 100, "ranking": 1}}],
            "loanGrps": [
              {"loanGrp": {"age": "40대", "gender": "여성", "loanCnt": 384, "ranking": 144}},
              {"loanGrp": {"age": "30대", "gender": "여성", "loanCnt": 288, "ranking": 25}}
            ],
            "keywords": [
              {"keyword": {"word": "최은영", "weight": "7"}},
              {"keyword": {"word": "쇼코의 미소", "weight": "6"}}
            ],
            "coLoanBooks": []
          }
        }
        """;

    private MockRestServiceServer server;
    private LibraryNaruService libraryNaruService;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        libraryNaruService = new LibraryNaruService(builder.build());
        ReflectionTestUtils.setField(libraryNaruService, "naruApiKey", "test-key");
        ReflectionTestUtils.setField(libraryNaruService, "naruApiUrl", API_URL);
    }

    @Test
    @DisplayName("정상 응답이면 이용 분석 정보를 파싱해서 반환한다.")
    void getUsageAnalysis_ParsesSuccessResponse() {
        server.expect(requestTo(startsWith(API_URL)))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        LibraryNaruUsageAnalysisResponse.UsageAnalysis result = libraryNaruService.getUsageAnalysis(ISBN);

        assertThat(result).isNotNull();
        assertThat(result.book().loanCnt()).isEqualTo(104490);
        assertThat(result.loanGrps()).hasSize(2);
        assertThat(result.loanGrps().get(0).loanGrp().age()).isEqualTo("40대");
        assertThat(result.loanGrps().get(0).loanGrp().loanCnt()).isEqualTo(384);
        assertThat(result.keywords()).hasSize(2);
        assertThat(result.keywords().get(0).keyword().word()).isEqualTo("최은영");
        // 가중치는 문자열로 내려오지만 숫자로 매핑된다.
        assertThat(result.keywords().get(0).keyword().weight()).isEqualTo(7.0);
        server.verify();
    }

    @Test
    @DisplayName("요청에 ISBN과 JSON 응답 형식을 함께 전달한다.")
    void getUsageAnalysis_SendsRequiredQueryParams() {
        server.expect(requestTo(containsString("isbn13=" + ISBN)))
            .andExpect(requestTo(containsString("format=json")))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        libraryNaruService.getUsageAnalysis(ISBN);

        server.verify();
    }

    @Test
    @DisplayName("정보나루가 HTTP 200으로 errCode를 내려주면 null을 반환한다.")
    void getUsageAnalysis_ReturnsNullOnErrorCode() {
        // 인증 실패나 잘못된 ISBN도 200으로 내려오므로 errCode로 판별해야 한다.
        String body = """
            {"response": {"errCode": "authErr", "error": "인증정보가 일치하지 않습니다."}}
            """;
        server.expect(requestTo(startsWith(API_URL)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(libraryNaruService.getUsageAnalysis(ISBN)).isNull();
        server.verify();
    }

    @Test
    @DisplayName("외부 API가 서버 오류를 반환해도 예외를 던지지 않고 null을 반환한다.")
    void getUsageAnalysis_ReturnsNullOnServerError() {
        server.expect(requestTo(startsWith(API_URL))).andRespond(withServerError());

        assertThat(libraryNaruService.getUsageAnalysis(ISBN)).isNull();
        server.verify();
    }

    @Test
    @DisplayName("외부 API 호출이 타임아웃되어도 예외를 던지지 않고 null을 반환한다.")
    void getUsageAnalysis_ReturnsNullOnTimeout() {
        server.expect(requestTo(startsWith(API_URL)))
            .andRespond(request -> {
                throw new IOException("connect timed out");
            });

        assertThat(libraryNaruService.getUsageAnalysis(ISBN)).isNull();
        server.verify();
    }

    @Test
    @DisplayName("응답을 파싱할 수 없어도 예외를 던지지 않고 null을 반환한다.")
    void getUsageAnalysis_ReturnsNullOnUnparsableBody() {
        server.expect(requestTo(startsWith(API_URL)))
            .andRespond(withSuccess("<html>error page</html>", MediaType.APPLICATION_JSON));

        assertThat(libraryNaruService.getUsageAnalysis(ISBN)).isNull();
        server.verify();
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 null을 반환한다.")
    void getUsageAnalysis_ReturnsNullOnEmptyBody() {
        server.expect(requestTo(startsWith(API_URL)))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(libraryNaruService.getUsageAnalysis(ISBN)).isNull();
        server.verify();
    }
}
