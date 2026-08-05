package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.PostCreateRequest;
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
    @Mock private GroupRepository groupRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private S3Service s3Service;
    @Mock private CursorUtils cursorUtils;

    @InjectMocks
    private PostService postService;

    private static final Long POST_ID = 10L;
    private static final String ISBN = "9780132350884";

    private String stubPostDetail(String bookTitle) {
        String userPK = UUID.randomUUID().toString();

        BookInfo bookInfo = mock(BookInfo.class);
        given(bookInfo.getIsbn()).willReturn(ISBN);

        User uploader = mock(User.class);
        given(uploader.getNickname()).willReturn("writer");
        given(uploader.getProfileImageKey()).willReturn(null);

        User viewer = mock(User.class);
        Post post = mock(Post.class);
        given(post.getPostId()).willReturn(POST_ID);
        given(post.getBookInfo()).willReturn(bookInfo);
        given(post.getBookTitle()).willReturn(bookTitle);
        given(post.getUploader()).willReturn(uploader);
        given(post.getImages()).willReturn(List.of());
        given(post.getGroup()).willReturn(null);
        given(post.getContent()).willReturn("post content");
        given(post.getLikeCount()).willReturn(0);
        given(post.getCreatedAt()).willReturn(LocalDateTime.of(2026, 8, 3, 12, 0));

        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(userRepository.findById(userPK)).willReturn(Optional.of(viewer));
        given(postCommentRepository.countByPost(post)).willReturn(0L);
        given(postLikeRepository.existsByPostAndUser(post, viewer)).willReturn(false);

        return userPK;
    }

    @Test
    @DisplayName("게시글 상세 조회는 게시글에 저장된 도서 제목을 내려준다.")
    void getPostDetail_UsesPostBookTitle() {
        String userPK = stubPostDetail("Clean Code");

        PostDetailResponse response = postService.getPostDetail(POST_ID, userPK);

        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.isbn()).isEqualTo(ISBN);
    }

    // 게시글 작성 요청을 stubbing하고, 작성에 사용된 BookInfo를 돌려준다.
    private BookInfo stubCreate(BookInfo bookInfo) {
        given(bookInfoRepository.findByIsbn(ISBN)).willReturn(Optional.of(bookInfo));
        given(userRepository.findById(anyString())).willReturn(Optional.of(mock(User.class)));
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> invocation.getArgument(0));
        return bookInfo;
    }

    @Test
    @DisplayName("게시글 작성 시 요청의 도서 제목을 게시글과 BookInfo에 저장한다.")
    void create_StoresRequestedTitle() {
        BookInfo bookInfo = stubCreate(BookInfo.builder().isbn(ISBN).build());
        PostCreateRequest request =
            new PostCreateRequest(ISBN, "Clean Code", "post content", List.of(), null);

        var response = postService.create(request, UUID.randomUUID().toString());

        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("BookInfo 제목은 덮어쓰지 않지만 게시글에는 작성 요청의 제목을 저장한다.")
    void create_KeepsAlreadyStoredTitle() {
        BookInfo bookInfo = stubCreate(BookInfo.builder().isbn(ISBN).title("Clean Code").build());
        PostCreateRequest request =
            new PostCreateRequest(ISBN, "오타난 제목", "post content", List.of(), null);

        var response = postService.create(request, UUID.randomUUID().toString());

        assertThat(response.title()).isEqualTo("오타난 제목");
        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("작성 요청의 도서 제목이 공백이면 INVALID_INPUT_VALUE 예외를 던진다.")
    void create_ThrowsWhenTitleIsBlank() {
        PostCreateRequest request =
            new PostCreateRequest(ISBN, "   ", "post content", List.of(), null);

        assertThatThrownBy(() -> postService.create(request, UUID.randomUUID().toString()))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
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
