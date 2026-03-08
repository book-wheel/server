package com.bookwheel.server.community.service;

import com.bookwheel.server.book.entity.Book;
import com.bookwheel.server.book.repository.BookRepository;
import com.bookwheel.server.common.exception.BusinessException;
import com.bookwheel.server.common.exception.ErrorCode;
import com.bookwheel.server.community.dto.PostCreateRequest;
import com.bookwheel.server.community.dto.PostCreateResponse;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
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
    private final BookRepository bookRepository;

    @Transactional
    public PostCreateResponse create(String bookId, PostCreateRequest request, String userId) {
        Book book = bookRepository.findById(bookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        User user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = request.toEntity(book, user);

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

}
