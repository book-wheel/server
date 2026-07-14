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
    @Mock private S3Service s3Service;

    @InjectMocks
    private BookService bookService;

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
        given(bookInfo.getBookInfoId()).willReturn(1L);
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
}
