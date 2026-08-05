package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import static org.mockito.Mockito.mock;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.dto.BookSearchListResponse;
import com.bookwheel.server.community.dto.BookSearchRequest;
import com.bookwheel.server.community.dto.BookSearchResponse;
import com.bookwheel.server.community.dto.BookUsageAnalysisResponse;
import com.bookwheel.server.community.dto.GalleryResponseDto;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.BookLikeRepository;
import com.bookwheel.server.community.repository.BookReviewRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.community.repository.ReviewLikeRepository;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookReviewRepository bookReviewRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BookLikeRepository bookLikeRepository;
    @Mock private PostRepository postRepository;
    @Mock private CursorUtils cursorUtils;
    @Mock private KaKaoService kaKaoService;
    @Mock private AladinService aladinService;
    @Mock private LibraryNaruService libraryNaruService;
    @Mock private S3Service s3Service;

    @InjectMocks
    private BookService bookService;

    @Test
    @DisplayName("도서 검색 결과에 현재 사용자의 관심 도서 여부를 표시한다.")
    void searchBooks_MarksInterestedBooks() {
        String userPK = UUID.randomUUID().toString();
        BookSearchRequest request = new BookSearchRequest("clean code", null, null, null);
        BookSearchResponse interestedBook = new BookSearchResponse(
                "Clean Code",
                "Robert C. Martin",
                "Prentice Hall",
                "2008-08-01",
                "https://example.com/clean-code.jpg",
                "9780132350884",
                false
        );
        BookSearchResponse otherBook = new BookSearchResponse(
                "Refactoring",
                "Martin Fowler",
                "Addison-Wesley",
                "2018-11-19",
                "https://example.com/refactoring.jpg",
                "9780134757599",
                false
        );
        BookSearchListResponse kakaoResponse = new BookSearchListResponse(
                List.of(interestedBook, otherBook),
                2,
                true
        );

        given(kaKaoService.searchBooks(request)).willReturn(kakaoResponse);
        given(bookLikeRepository.findInterestedIsbns(
                userPK,
                List.of("9780132350884", "9780134757599")
        )).willReturn(List.of("9780132350884"));

        BookSearchListResponse response = bookService.searchBooks(request, userPK);

        assertThat(response.books()).hasSize(2);
        assertThat(response.books().get(0).isbn()).isEqualTo("9780132350884");
        assertThat(response.books().get(0).isInterested()).isTrue();
        assertThat(response.books().get(1).isbn()).isEqualTo("9780134757599");
        assertThat(response.books().get(1).isInterested()).isFalse();
    }

    @Test
    @DisplayName("갤러리 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getGallery_ThrowsWhenSizeExceedsMax() {
        assertThatThrownBy(() -> bookService.getGallery(null, 51))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("특정 책 갤러리 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getGalleryByIsbn_ThrowsWhenSizeExceedsMax() {
        assertThatThrownBy(() -> bookService.getGalleryByIsbn("9788934972464", null, 51))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("관심 도서 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getInterestBooks_ThrowsWhenSizeExceedsMax() {
        String userPK = UUID.randomUUID().toString();
        given(userRepository.existsById(userPK)).willReturn(true);

        assertThatThrownBy(() -> bookService.getInterestBooks(null, 51, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("갤러리 size가 상한(50)과 같으면 예외 없이 통과한다.")
    void getGallery_AllowsSizeAtMax() {
        given(cursorUtils.decode(null, GalleryCursor.class)).willReturn(null);
        given(postRepository.findGalleryPage(null, 51)).willReturn(List.of());
        given(postRepository.countGalleryPosts()).willReturn(0L);

        // size=50 이면 상한 이내이므로 예외가 발생하지 않아야 한다.
        assertThat(bookService.getGallery(null, 50)).isNotNull();
    }

    @Test
    @DisplayName("갤러리 thumbnailUrl은 objectKey가 아닌 Presigned URL로 변환되어 반환된다.")
    void getGallery_ConvertsThumbnailToPresignedUrl() {
        String objectKey = "posts/1/thumbnail.jpg";
        String presignedUrl = "https://bucket.s3.amazonaws.com/posts/1/thumbnail.jpg?X-Amz-Signature=abc";

        PostImage image = mock(PostImage.class);
        given(image.getObjectKey()).willReturn(objectKey);

        BookInfo bookInfo = mock(BookInfo.class);
        given(bookInfo.getIsbn()).willReturn("9788934972464");

        Post post = mock(Post.class);
        given(post.getImages()).willReturn(List.of(image));
        given(post.getBookInfo()).willReturn(bookInfo);
        given(post.getPostId()).willReturn(10L);
        given(post.getCreatedAt()).willReturn(LocalDateTime.of(2026, 7, 14, 0, 0));

        given(cursorUtils.decode(null, GalleryCursor.class)).willReturn(null);
        given(postRepository.findGalleryPage(null, 19)).willReturn(List.of(post));
        given(postRepository.countGalleryPosts()).willReturn(1L);
        given(s3Service.getPresignedGetUrl(objectKey)).willReturn(presignedUrl);

        CursorPageResponse<GalleryResponseDto> response = bookService.getGallery(null, null);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).thumbnailUrl()).isEqualTo(presignedUrl);
    }

    private BookDetailResponse sampleBookDetail(String isbn) {
        return new BookDetailResponse(
                "밝은 밤",
                "최은영",
                "문학동네",
                "책 소개",
                "https://image.aladin.co.kr/cover.jpg",
                340,
                isbn,
                true,
                null
        );
    }

    private BookUsageAnalysisResponse sampleUsageAnalysis() {
        return new BookUsageAnalysisResponse(104490, "40대", List.of("최은영"));
    }

    @Test
    @DisplayName("도서 상세 조회 응답에 도서관정보나루 이용 분석 정보가 함께 담긴다.")
    void getBookDetail_IncludesUsageAnalysis() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();

        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK)).willReturn(true);
        given(aladinService.getBookDetailByIsbn(isbn, true)).willReturn(sampleBookDetail(isbn));
        given(libraryNaruService.getUsageAnalysis(isbn)).willReturn(sampleUsageAnalysis());

        BookDetailResponse response = bookService.getBookDetail(isbn, userPK);

        BookUsageAnalysisResponse usageAnalysis = response.usageAnalysis();
        assertThat(usageAnalysis).isNotNull();
        assertThat(usageAnalysis.totalLoanCount()).isEqualTo(104490);
        assertThat(usageAnalysis.mostLoanedAgeGroup()).isEqualTo("40대");
        assertThat(usageAnalysis.keywords()).containsExactly("최은영");
    }

    @Test
    @DisplayName("도서관정보나루 조회에 실패해도 도서 상세 조회는 성공하고 usageAnalysis만 null이 된다.")
    void getBookDetail_SucceedsWhenUsageAnalysisUnavailable() {
        String isbn = "9788954681179";
        String userPK = UUID.randomUUID().toString();

        given(bookLikeRepository.existsByBookInfo_IsbnAndUserPK(isbn, userPK)).willReturn(true);
        given(aladinService.getBookDetailByIsbn(isbn, true)).willReturn(sampleBookDetail(isbn));
        // 외부 API 호출 실패, 타임아웃, 데이터 없음은 모두 null로 넘어온다.
        given(libraryNaruService.getUsageAnalysis(isbn)).willReturn(null);

        BookDetailResponse response = bookService.getBookDetail(isbn, userPK);

        assertThat(response.usageAnalysis()).isNull();
        // 기존 도서 상세 필드는 그대로 유지되어야 한다.
        assertThat(response.title()).isEqualTo("밝은 밤");
        assertThat(response.author()).isEqualTo("최은영");
        assertThat(response.isbn()).isEqualTo(isbn);
        assertThat(response.itemPage()).isEqualTo(340);
        assertThat(response.isInterested()).isTrue();
    }
}
