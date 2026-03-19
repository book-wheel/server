package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostCommentCreateRequest;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCreateResponse;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.community.repository.BookInfoRepository;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
import com.bookwheel.server.user.entity.User;
import com.bookwheel.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    @Transactional
    public PostCreateResponse create(String bookInfoId, PostCreateRequest request, String userPk) {
        BookInfo bookInfo = bookInfoRepository.findById(bookInfoId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        User user = userRepository.findById(userPk)
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
    public void togglePostLike(Long postId, String userPk) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        User user = userRepository.findById(userPk)
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
                }
            );
    }

    @Transactional
    public void createPostComment(Long postId, PostCommentCreateRequest request, String userPk) {

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findById(userPk)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        PostComment comment = request.toEntity(post, user);

        postCommentRepository.save(comment);
    }

}
