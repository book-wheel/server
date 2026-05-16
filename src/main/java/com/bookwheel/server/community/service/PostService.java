package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostCommentCreateRequest;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCreateResponse;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.community.event.PostCommentedEvent;
import com.bookwheel.server.community.event.PostLikedEvent;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final BookInfoRepository bookInfoRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public PostCreateResponse create(String bookInfoId, PostCreateRequest request, String userId) {
        BookInfo bookInfo = bookInfoRepository.findById(bookInfoId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = request.toEntity(bookInfo, user);

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
    public void togglePostLike(Long postId, String userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findByUserId(userId)
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
                    String ownerUserId = post.getUploader().getUserId();
                    if (!ownerUserId.equals(userId)) {
                        eventPublisher.publishEvent(new PostLikedEvent(
                                post.getPostId(),
                                ownerUserId,
                                userId,
                                user.getNickname()
                        ));
                    }
                }
            );
    }

    @Transactional
    public void createPostComment(Long postId, PostCommentCreateRequest request, String userId) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PostComment comment = request.toEntity(post, user);

        postCommentRepository.save(comment);

        String ownerUserId = post.getUploader().getUserId();
        if (!ownerUserId.equals(userId)) {
            String preview = comment.getContent();
            if (preview != null && preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            eventPublisher.publishEvent(new PostCommentedEvent(
                    post.getPostId(),
                    ownerUserId,
                    userId,
                    user.getNickname(),
                    preview
            ));
        }
    }

}
