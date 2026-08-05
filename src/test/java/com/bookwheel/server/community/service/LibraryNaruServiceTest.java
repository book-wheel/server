package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.BookUsageAnalysisResponse;
import com.bookwheel.server.community.dto.LibraryNaruPopularLoanResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
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
    private static final String POPULAR_LOAN_API_URL = "https://data4library.kr/api/loanItemSrch";
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
        RestClient restClient = builder.build();

        libraryNaruService = new LibraryNaruService(restClient, restClient);
        ReflectionTestUtils.setField(libraryNaruService, "naruApiKey", "test-key");
        ReflectionTestUtils.setField(libraryNaruService, "naruApiUrl", API_URL);
        ReflectionTestUtils.setField(libraryNaruService, "naruPopularLoanApiUrl", POPULAR_LOAN_API_URL);
    }

    @Test
    @DisplayName("정상 응답이면 이용 분석 정보를 파싱해서 반환한다.")
    void getUsageAnalysis_ParsesSuccessResponse() {
        server.expect(requestTo(startsWith(API_URL)))
            .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        BookUsageAnalysisResponse result = libraryNaruService.getUsageAnalysis(ISBN);

        assertThat(result).isNotNull();
        assertThat(result.totalLoanCount()).isEqualTo(104490);
        // ranking(40대=144, 30대=25)이 아니라 loanCnt(384 > 288) 기준으로 선택된다.
        assertThat(result.mostLoanedAgeGroup()).isEqualTo("40대");
        // 가중치는 문자열("7")로 내려오지만 숫자로 매핑되어 내림차순 정렬된다.
        assertThat(result.keywords()).containsExactly("최은영", "쇼코의 미소");
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

    @Test
    @DisplayName("정보나루 인기대출도서 응답을 파싱한다")
    void getPopularLoanBooks_ParsesSuccessResponse() {
        String body = """
            {
              "response": {
                "resultNum": 2,
                "numFound": 5000,
                "docs": [
                  {
                    "doc": {
                      "ranking": 1,
                      "bookname": "Bright Night",
                      "authors": "Author A",
                      "publisher": "Publisher A",
                      "publication_year": "2021",
                      "isbn13": "9788954681179",
                      "loan_count": "104490"
                    }
                  },
                  {
                    "doc": {
                      "ranking": 2,
                      "bookname": "Clean Code",
                      "authors": "Robert C. Martin",
                      "publisher": "Prentice Hall",
                      "publication_year": "2008",
                      "isbn13": "9780132350884",
                      "loan_count": 90000
                    }
                  }
                ]
              }
            }
            """;
        server.expect(requestTo(startsWith(POPULAR_LOAN_API_URL)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<LibraryNaruPopularLoanResponse.Doc> result = libraryNaruService.getPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            1,
            1000
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).ranking()).isEqualTo(1);
        assertThat(result.get(0).isbn()).isEqualTo("9788954681179");
        assertThat(result.get(0).loanCount()).isEqualTo(104490);
        assertThat(result.get(1).ranking()).isEqualTo(2);
        assertThat(result.get(1).loanCount()).isEqualTo(90000);
        server.verify();
    }

    @Test
    @DisplayName("정보나루 인기대출도서 조회에 기간과 JSON 응답 형식을 전달한다")
    void getPopularLoanBooks_SendsRequiredQueryParams() {
        String body = """
            {"response": {"docs": []}}
            """;
        server.expect(requestTo(containsString("startDt=2026-07-01")))
            .andExpect(requestTo(containsString("endDt=2026-07-31")))
            .andExpect(requestTo(containsString("pageNo=1")))
            .andExpect(requestTo(containsString("pageSize=1000")))
            .andExpect(requestTo(containsString("format=json")))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        List<LibraryNaruPopularLoanResponse.Doc> result = libraryNaruService.getPopularLoanBooks(
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            1,
            1000
        );

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("정보나루 인기대출도서가 errCode를 내려주면 DATA4LIBRARY_API_ERROR를 던진다")
    void getPopularLoanBooks_ThrowsOnErrorCode() {
        String body = """
            {"response": {"errCode": "authErr", "error": "invalid auth key"}}
            """;
        server.expect(requestTo(startsWith(POPULAR_LOAN_API_URL)))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertData4LibraryApiError(() -> libraryNaruService.getPopularLoanBooks(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                1,
                1000
            )
        );
        server.verify();
    }

    @Test
    @DisplayName("정보나루 인기대출도서 호출이 실패하면 DATA4LIBRARY_API_ERROR를 던진다")
    void getPopularLoanBooks_ThrowsOnServerError() {
        server.expect(requestTo(startsWith(POPULAR_LOAN_API_URL))).andRespond(withServerError());

        assertData4LibraryApiError(() -> libraryNaruService.getPopularLoanBooks(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                1,
                1000
            )
        );
        server.verify();
    }

    @Test
    @DisplayName("정보나루 인기대출도서 연결이 실패하면 DATA4LIBRARY_API_ERROR를 던진다")
    void getPopularLoanBooks_ThrowsOnConnectionFailure() {
        server.expect(requestTo(startsWith(POPULAR_LOAN_API_URL)))
            .andRespond(request -> {
                throw new IOException("connect timed out");
            });

        assertData4LibraryApiError(() -> libraryNaruService.getPopularLoanBooks(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                1,
                1000
            )
        );
        server.verify();
    }

    private void assertData4LibraryApiError(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).getErrorCode())
            .isEqualTo(ErrorCode.DATA4LIBRARY_API_ERROR);
    }
}
