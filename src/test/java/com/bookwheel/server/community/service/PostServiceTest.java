package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.BookDetailResponse;
import com.bookwheel.server.community.dto.PostDetailResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostReportRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private BookInfoRepository bookInfoRepository;
    @Mock private BookRepository bookRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private S3Service s3Service;
    @Mock private CursorUtils cursorUtils;
    @Mock private AladinService aladinService;

    @InjectMocks
    private PostService postService;

    @Test
    @DisplayName("Book 테이블에 도서가 없어도 게시글 상세 title은 null이 아니다.")
    void getPostDetail_TitleIsNonNullWhenBookIsMissing() {
        Long postId = 10L;
        String userPK = UUID.randomUUID().toString();
        String isbn = "9780132350884";

        BookInfo bookInfo = mock(BookInfo.class);
        given(bookInfo.getIsbn()).willReturn(isbn);

        User uploader = mock(User.class);
        given(uploader.getNickname()).willReturn("writer");
        given(uploader.getProfileImageKey()).willReturn(null);

        User viewer = mock(User.class);
        Post post = mock(Post.class);
        given(post.getPostId()).willReturn(postId);
        given(post.getBookInfo()).willReturn(bookInfo);
        given(post.getUploader()).willReturn(uploader);
        given(post.getImages()).willReturn(List.of());
        given(post.getGroup()).willReturn(null);
        given(post.getContent()).willReturn("post content");
        given(post.getLikeCount()).willReturn(0);
        given(post.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 3, 12, 0));

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(userRepository.findById(userPK)).willReturn(Optional.of(viewer));
        given(bookRepository.findByIsbn(isbn)).willReturn(Optional.empty());
        given(aladinService.getBookDetailByIsbn(isbn, false)).willReturn(new BookDetailResponse(
                "Clean Code",
                "Robert C. Martin",
                "Prentice Hall",
                "A handbook of agile software craftsmanship.",
                "https://example.com/clean-code.jpg",
                464,
                "Contents",
                isbn,
                false
        ));
        given(postCommentRepository.countByPost(post)).willReturn(0L);
        given(postLikeRepository.existsByPostAndUser(post, viewer)).willReturn(false);

        PostDetailResponse response = postService.getPostDetail(postId, userPK);

        assertThat(response.title()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("댓글 size가 상한(50)을 초과하면 INVALID_INPUT_VALUE 예외를 던진다.")
    void getPostComments_ThrowsWhenSizeExceedsMax() {
        Long postId = 7L;
        String userPK = UUID.randomUUID().toString();
        given(postRepository.findById(postId)).willReturn(Optional.of(mock(Post.class)));
        given(userRepository.existsById(userPK)).willReturn(true);

        assertThatThrownBy(() -> postService.getPostComments(postId, null, 51, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
