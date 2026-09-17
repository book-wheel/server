package com.bookwheel.server.community.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;

import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCommentResponse;
import com.bookwheel.server.community.dto.PostDetailResponse;
import com.bookwheel.server.community.entity.BookInfo;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostComment;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.event.PostLikedEvent;
import com.bookwheel.server.community.image.PostThumbnailService;
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
import org.mockito.ArgumentCaptor;
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
    @Mock private PostDeletionService postDeletionService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private S3Service s3Service;
    @Mock private CursorUtils cursorUtils;
    @Mock private PostThumbnailService postThumbnailService;

    @InjectMocks
    private PostService postService;

    private static final Long POST_ID = 10L;
    private static final String ISBN = "9780132350884";

    private String stubPostDetail(String bookTitle) {
        String userPK = UUID.randomUUID().toString();

        BookInfo bookInfo = mock(BookInfo.class);
        given(bookInfo.getIsbn()).willReturn(ISBN);

        User uploader = mock(User.class);
        given(uploader.getId()).willReturn(userPK);
        given(uploader.getIsActive()).willReturn(true);
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
        assertThat(response.isMine()).isTrue();
    }

    @Test
    @DisplayName("탈퇴자의 게시글은 익명 작성자로 조회된다")
    void getPostDetail_ShowsAnonymousAuthorWhenUploaderIsDetached() {
        String userPK = UUID.randomUUID().toString();
        User viewer = mock(User.class);
        Post post = mock(Post.class);
        BookInfo bookInfo = mock(BookInfo.class);

        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(userRepository.findById(userPK)).willReturn(Optional.of(viewer));
        given(post.getBookInfo()).willReturn(bookInfo);
        given(bookInfo.getIsbn()).willReturn(ISBN);
        given(post.getUploader()).willReturn(null);
        given(post.getImages()).willReturn(List.of());
        given(post.getContent()).willReturn("보존된 게시글");
        given(postCommentRepository.countByPost(post)).willReturn(0L);

        PostDetailResponse response = postService.getPostDetail(POST_ID, userPK);

        assertThat(response.author()).isEqualTo("탈퇴한 사용자");
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.isMine()).isFalse();
    }

    @Test
    @DisplayName("탈퇴자의 게시글에 좋아요를 눌러도 알림 이벤트를 발행하지 않는다")
    void togglePostLike_SkipsNotificationForAnonymousAuthor() {
        String userPK = UUID.randomUUID().toString();
        User user = mock(User.class);
        Post post = Post.builder().postId(POST_ID).build();

        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(userRepository.findById(userPK)).willReturn(Optional.of(user));
        given(postLikeRepository.findByPostAndUser(post, user)).willReturn(Optional.empty());

        postService.togglePostLike(POST_ID, userPK);

        assertThat(post.getLikeCount()).isEqualTo(1);
        then(eventPublisher).should(never()).publishEvent(any(PostLikedEvent.class));
    }

    // 게시글 작성 요청을 stubbing하고, 작성에 사용된 BookInfo를 돌려준다.
    private BookInfo stubCreate(BookInfo bookInfo) {
        given(bookInfoRepository.findOrCreateByIsbn(ISBN)).willReturn(bookInfo);
        given(userRepository.findById(anyString())).willReturn(Optional.of(mock(User.class)));
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> invocation.getArgument(0));
        return bookInfo;
    }

    @Test
    @DisplayName("게시글 작성 시 요청의 도서 제목을 게시글과 BookInfo에 저장한다.")
    void create_StoresRequestedTitle() {
        BookInfo bookInfo = stubCreate(BookInfo.builder().isbn(ISBN).build());
        PostCreateRequest request =
            new PostCreateRequest("Clean Code", "post content", List.of(), null);

        var response = postService.create(ISBN, request, UUID.randomUUID().toString());

        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("BookInfo 제목은 덮어쓰지 않지만 게시글에는 작성 요청의 제목을 저장한다.")
    void create_KeepsAlreadyStoredTitle() {
        BookInfo bookInfo = stubCreate(BookInfo.builder().isbn(ISBN).title("Clean Code").build());
        PostCreateRequest request =
            new PostCreateRequest("오타난 제목", "post content", List.of(), null);

        var response = postService.create(ISBN, request, UUID.randomUUID().toString());

        assertThat(response.title()).isEqualTo("오타난 제목");
        assertThat(bookInfo.getTitle()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("작성 요청의 도서 제목이 공백이면 INVALID_INPUT_VALUE 예외를 던진다.")
    void create_ThrowsWhenTitleIsBlank() {
        PostCreateRequest request =
            new PostCreateRequest("   ", "post content", List.of(), null);

        assertThatThrownBy(() -> postService.create(ISBN, request, UUID.randomUUID().toString()))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }

    @Test
    @DisplayName("게시글 작성 시 URL 경로의 ISBN으로 BookInfo를 생성한다")
    void create_CreatesBookInfoWithPathIsbn() {
        String pathIsbn = "9791161571188";
        PostCreateRequest request =
            new PostCreateRequest("Clean Code", "post content", List.of(), null);
        given(bookInfoRepository.findOrCreateByIsbn(pathIsbn))
            .willReturn(BookInfo.builder().isbn(pathIsbn).build());
        given(userRepository.findById(anyString())).willReturn(Optional.of(mock(User.class)));
        given(postRepository.save(any(Post.class))).willAnswer(invocation -> invocation.getArgument(0));

        var response = postService.create(pathIsbn, request, UUID.randomUUID().toString());

        assertThat(response.isbn()).isEqualTo(pathIsbn);
        then(bookInfoRepository).should().findOrCreateByIsbn(pathIsbn);
    }

    @Test
    @DisplayName("이미지를 저장한 뒤 썸네일 생성을 커밋 이후로 예약한다")
    void create_SchedulesThumbnailGenerationAfterCommit() {
        stubCreate(BookInfo.builder().isbn(ISBN).build());
        String objectKey = "posts/" + ISBN + "/abc_image.png";
        PostCreateRequest request =
            new PostCreateRequest("Clean Code", "post content", List.of(objectKey), null);

        postService.create(ISBN, request, UUID.randomUUID().toString());

        ArgumentCaptor<Post> savedPost = ArgumentCaptor.forClass(Post.class);
        then(postRepository).should().save(savedPost.capture());
        List<PostImage> savedImages = savedPost.getValue().getImages();
        assertThat(savedImages).hasSize(1);
        assertThat(savedImages.get(0).getObjectKey()).isEqualTo(objectKey);

        // 썸네일 키는 커밋 이후에 채워진다. 저장 시점에는 비어 있어야 한다.
        assertThat(savedImages.get(0).getThumbnailKey()).isNull();
        then(postThumbnailService).should().registerPostCommitThumbnailGeneration(savedImages);
    }

    @Test
    @DisplayName("썸네일 생성은 게시물 저장 트랜잭션 안에서 MinIO를 호출하지 않는다")
    void create_DoesNotCallThumbnailGenerationInline() {
        stubCreate(BookInfo.builder().isbn(ISBN).build());
        String objectKey = "posts/" + ISBN + "/abc_image.png";
        PostCreateRequest request =
            new PostCreateRequest("Clean Code", "post content", List.of(objectKey), null);

        postService.create(ISBN, request, UUID.randomUUID().toString());

        // 트랜잭션 안에서 돌면 모임 행 잠금과 DB 커넥션을 MinIO 왕복 내내 붙잡게 된다.
        then(postThumbnailService).should(never()).createThumbnail(anyString());
    }

    @Test
    @DisplayName("이미지가 없으면 썸네일 생성을 예약하지 않는다")
    void create_SkipsThumbnailSchedulingWithoutImages() {
        stubCreate(BookInfo.builder().isbn(ISBN).build());
        PostCreateRequest request =
            new PostCreateRequest("Clean Code", "post content", List.of(), null);

        postService.create(ISBN, request, UUID.randomUUID().toString());

        then(postThumbnailService).should().registerPostCommitThumbnailGeneration(List.of());
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

    @Test
    @DisplayName("탈퇴자의 댓글은 익명 작성자로 조회된다")
    void getPostComments_ShowsAnonymousAuthor() {
        Long postId = 7L;
        String userPK = UUID.randomUUID().toString();
        Post post = mock(Post.class);
        PostComment comment = PostComment.builder()
                .postCommentId(3L)
                .post(post)
                .content("보존된 댓글")
                .build();

        given(postRepository.findById(postId)).willReturn(Optional.of(post));
        given(userRepository.existsById(userPK)).willReturn(true);
        given(postCommentRepository.findFirstCommentPage(eq(post), any())).willReturn(List.of(comment));
        given(postCommentRepository.countByPost(post)).willReturn(1L);

        var response = postService.getPostComments(postId, null, 20, userPK);
        PostCommentResponse result = response.content().get(0);

        assertThat(result.author()).isEqualTo("탈퇴한 사용자");
        assertThat(result.profileImageUrl()).isNull();
        assertThat(result.isMine()).isFalse();
    }

    @Test
    @DisplayName("게시물 댓글 삭제는 작성자 본인이면 댓글을 삭제한다")
    void deletePostComment_DeletesWhenOwner() {
        Long postId = 7L;
        Long commentId = 3L;
        String userPK = UUID.randomUUID().toString();
        PostComment comment = mock(PostComment.class);
        User user = mock(User.class);

        given(postCommentRepository.findByPostCommentIdAndPost_PostId(commentId, postId))
                .willReturn(Optional.of(comment));
        given(comment.getUser()).willReturn(user);
        given(user.getId()).willReturn(userPK);
        given(user.getIsActive()).willReturn(true);

        postService.deletePostComment(postId, commentId, userPK);

        then(postCommentRepository).should().delete(comment);
    }

    @Test
    @DisplayName("게시물 댓글 삭제는 작성자 본인이 아니면 예외가 발생한다")
    void deletePostComment_ThrowsWhenNotOwner() {
        Long postId = 7L;
        Long commentId = 3L;
        String userPK = UUID.randomUUID().toString();
        PostComment comment = mock(PostComment.class);
        User user = mock(User.class);

        given(postCommentRepository.findByPostCommentIdAndPost_PostId(commentId, postId))
                .willReturn(Optional.of(comment));
        given(comment.getUser()).willReturn(user);
        given(user.getId()).willReturn(UUID.randomUUID().toString());
        given(user.getIsActive()).willReturn(true);

        assertThatThrownBy(() -> postService.deletePostComment(postId, commentId, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_COMMENT_DELETE_FORBIDDEN));

        then(postCommentRepository).should(never()).delete(any(PostComment.class));
    }

    @Test
    @DisplayName("작성자 연결이 해제된 댓글은 일반 회원이 삭제할 수 없다")
    void deletePostComment_RejectsAnonymousComment() {
        Long postId = 7L;
        Long commentId = 3L;
        String userPK = UUID.randomUUID().toString();
        PostComment comment = mock(PostComment.class);

        given(postCommentRepository.findByPostCommentIdAndPost_PostId(commentId, postId))
                .willReturn(Optional.of(comment));
        given(comment.getUser()).willReturn(null);

        assertThatThrownBy(() -> postService.deletePostComment(postId, commentId, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_COMMENT_DELETE_FORBIDDEN));

        then(postCommentRepository).should(never()).delete(any(PostComment.class));
    }

    @Test
    @DisplayName("게시물 삭제는 작성자 본인이면 게시물을 삭제한다")
    void deletePost_DeletesWhenOwner() {
        String userPK = UUID.randomUUID().toString();
        Post post = mock(Post.class);
        User uploader = mock(User.class);

        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(post.getUploader()).willReturn(uploader);
        given(uploader.getId()).willReturn(userPK);
        given(uploader.getIsActive()).willReturn(true);

        postService.deletePost(POST_ID, userPK);

        then(postDeletionService).should().delete(post);
    }

    @Test
    @DisplayName("게시물 삭제는 작성자 본인이 아니면 예외가 발생한다")
    void deletePost_ThrowsWhenNotOwner() {
        String userPK = UUID.randomUUID().toString();
        Post post = mock(Post.class);
        User uploader = mock(User.class);

        given(postRepository.findById(POST_ID)).willReturn(Optional.of(post));
        given(post.getUploader()).willReturn(uploader);
        given(uploader.getId()).willReturn(UUID.randomUUID().toString());
        given(uploader.getIsActive()).willReturn(true);

        assertThatThrownBy(() -> postService.deletePost(POST_ID, userPK))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_DELETE_FORBIDDEN));

        then(postDeletionService).should(never()).delete(any(Post.class));
    }
}
