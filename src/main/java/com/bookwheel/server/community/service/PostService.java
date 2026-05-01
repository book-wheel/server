package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostCommentCreateRequest;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCreateResponse;
import com.bookwheel.server.community.dto.PostReportRequest;
import com.bookwheel.server.community.entity.*;
import com.bookwheel.server.community.repository.*;
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
    private final PostReportRepository postReportRepository;

    @Transactional
    public PostCreateResponse create(PostCreateRequest request, String userPK) {
        String isbn = request.isbn();
        BookInfo bookInfo = bookInfoRepository.findByIsbn(isbn)
            .orElseGet(() -> bookInfoRepository.save(BookInfo.builder().isbn(isbn).build()));

        User user = userRepository.findById(userPK)
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
