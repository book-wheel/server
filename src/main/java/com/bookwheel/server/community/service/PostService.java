package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.common.cursor.CommentCursor;
import com.bookwheel.server.common.response.CursorPageResponse;
import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.common.util.CursorUtils;
import com.bookwheel.server.community.dto.PostCommentCreateRequest;
import com.bookwheel.server.community.dto.PostCommentResponse;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCreateResponse;
import com.bookwheel.server.community.dto.PostDetailResponse;
import com.bookwheel.server.community.dto.PostReportRequest;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.community.event.PostCommentedEvent;
import com.bookwheel.server.community.event.PostLikedEvent;
import com.bookwheel.server.community.repository.*;
import com.bookwheel.server.group.entity.Group;
import com.bookwheel.server.group.repository.GroupRepository;
import com.bookwheel.server.member.enums.MemberStatus;
import com.bookwheel.server.member.repository.MemberRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BookInfoRepository bookInfoRepository;
    private final BookRepository bookRepository;
    private final GroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReportRepository postReportRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Service s3Service;
    private final CursorUtils cursorUtils;

    private static final int DEFAULT_COMMENT_SIZE = 20;
    private static final int MAX_COMMENT_PAGE_SIZE = 50;

    public CursorPageResponse<PostCommentResponse> getPostComments(Long postId, String cursor, Integer size, String userPK) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (!userRepository.existsById(userPK)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        int pageSize = resolveCommentPageSize(size);
        CommentCursor commentCursor = cursorUtils.decode(cursor, CommentCursor.class);
        validateCommentCursor(commentCursor);

        PageRequest pageRequest = PageRequest.of(0, pageSize + 1);
        List<PostComment> comments = (commentCursor == null)
            ? postCommentRepository.findFirstCommentPage(post, pageRequest)
            : postCommentRepository.findCommentPageAfterCursor(
                post, commentCursor.createdAt(), commentCursor.commentId(), pageRequest);

        boolean hasNext = comments.size() > pageSize;
        List<PostComment> pageComments = hasNext ? comments.subList(0, pageSize) : comments;

        List<PostCommentResponse> content = pageComments.stream()
            .map(comment -> PostCommentResponse.of(
                comment,
                getProfileImageUrl(comment.getUser().getProfileImageKey()),
                comment.getUser().getId().equals(userPK)
            ))
            .toList();

        String nextCursor = hasNext ? createNextCommentCursor(pageComments) : null;
        Long totalElements = commentCursor == null ? postCommentRepository.countByPost(post) : null;

        return CursorPageResponse.of(content, pageSize, totalElements, hasNext, nextCursor);
    }

    private int resolveCommentPageSize(Integer size) {
        if (size == null) {
            return DEFAULT_COMMENT_SIZE;
        }
        if (size <= 0 || size > MAX_COMMENT_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return size;
    }

    private void validateCommentCursor(CommentCursor cursor) {
        if (cursor == null) {
            return;
        }
        if (cursor.createdAt() == null || cursor.commentId() == null) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }

    private String createNextCommentCursor(List<PostComment> comments) {
        PostComment last = comments.get(comments.size() - 1);
        return cursorUtils.encode(new CommentCursor(last.getCreatedAt(), last.getPostCommentId()));
    }

    public PostDetailResponse getPostDetail(Long postId, String userPK) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String isbn = post.getBookInfo().getIsbn();

        // 책 제목은 별도 Book 테이블에서 조회한다. (미등록 도서면 null)
        String title = bookRepository.findByIsbn(isbn)
            .map(Book::getTitle)
            .orElse(null);

        String profileImageUrl = getProfileImageUrl(post.getUploader().getProfileImageKey());

        List<String> imageUrls = post.getImages().stream()
            .map(image -> s3Service.getPresignedGetUrl(image.getObjectKey()))
            .toList();

        long commentCount = postCommentRepository.countByPost(post);
        boolean isLikedByMe = postLikeRepository.existsByPostAndUser(post, user);

        // 모임에서 작성한 게시물이면 모임 이름, 개인 작성이면 null
        String groupName = post.getGroup() != null ? post.getGroup().getGroupName() : null;

        return new PostDetailResponse(
            post.getPostId(),
            isbn,
            post.getUploader().getNickname(),
            profileImageUrl,
            groupName,
            title,
            post.getContent(),
            imageUrls,
            post.getLikeCount(),
            commentCount,
            isLikedByMe,
            post.getCreatedAt()
        );
    }

    // 프로필 이미지 키를 조회용 Presigned URL로 변환한다. (키가 없으면 null)
    private String getProfileImageUrl(String profileImageKey) {
        if (!StringUtils.hasText(profileImageKey)) {
            return null;
        }
        return s3Service.getPresignedGetUrl(profileImageKey);
    }

    // 작성 요청의 groupId로 모임을 조회한다.
    // 모임 미지정이면 null, 없는 모임이면 GROUP_NOT_FOUND, 작성자가 그 모임의 활성 멤버가 아니면 GROUP_MEMBER_ONLY
    private Group resolveGroup(String groupId, String userPK) {
        if (!StringUtils.hasText(groupId)) {
            return null;
        }

        // 모임 게시물 생성과 모임 비활성화가 같은 group_id를 동시에 사용하지 않도록 그룹을 잠근다.
        Group group = groupRepository.findByGroupIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        // 잠금 이후 ACTIVE 멤버인지 확인해 삭제와 게시물 생성을 같은 순서로 직렬화한다.
        boolean isActiveMember = memberRepository
            .existsByGroup_GroupIdAndUser_IdAndMemberStatus(groupId, userPK, MemberStatus.ACTIVE);
        if (!isActiveMember) {
            throw new BusinessException(ErrorCode.GROUP_MEMBER_ONLY);
        }

        return group;
    }

    @Transactional
    public PostCreateResponse create(PostCreateRequest request, String userPK) {
        String isbn = request.isbn();
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn)
            .orElseGet(() -> bookInfoRepository.save(BookInfo.builder().isbn(isbn).build()));

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Group group = resolveGroup(request.groupId(), userPK);

        Post post = request.toEntity(bookInfo, user, group);

        if (request.objectKeys() != null && !request.objectKeys().isEmpty()) {
            for (String key : request.objectKeys()) {
                PostImage postImage = PostImage.builder()
                    .objectKey(key)
                    .build();
                post.addImage(postImage);
            }
        }

        Post savedPost = postRepository.save(post);

        return PostCreateResponse.from(savedPost);


    }

    @Transactional
    public void togglePostLike(Long postId, String userPK) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        postLikeRepository.findByPostAndUser(post, user)
            .ifPresentOrElse(
                postLike -> {
                    postLikeRepository.delete(postLike);
                    post.decreaseLikeCount();
                },

                () -> {
                    postLikeRepository.save(PostLike.create(post, user));
                    post.increaseLikeCount();
                    String ownerUserPK = post.getUploader().getId();
                    if (!ownerUserPK.equals(userPK)) {
                        eventPublisher.publishEvent(new PostLikedEvent(
                                post.getPostId(),
                                ownerUserPK,
                                userPK,
                                user.getNickname()
                        ));
                    }
                }
            );
    }

    @Transactional
    public void createPostComment(Long postId, PostCommentCreateRequest request, String userPK) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PostComment comment = request.toEntity(post, user);

        postCommentRepository.save(comment);

        String ownerUserPK = post.getUploader().getId();
        if (!ownerUserPK.equals(userPK)) {
            eventPublisher.publishEvent(new PostCommentedEvent(
                    post.getPostId(),
                    ownerUserPK,
                    userPK,
                    user.getNickname(),
                    comment.getContent()
            ));
        }
    }

    @Transactional
    public void reportPost(Long postId, PostReportRequest request, String userPK) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userPK)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (post.getUploader().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.CANNOT_REPORT_OWN_POST);
        }


        if (postReportRepository.existsByPostAndReporter(post, user)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        // 신고 내역 저장
        PostReport postReport = new PostReport(post, user, request.reason());
        postReportRepository.save(postReport);
    }



}
