package com.bookwheel.server.community.service;

import com.bookwheel.server.common.service.S3Service;
import com.bookwheel.server.community.entity.Post;
import com.bookwheel.server.community.entity.PostImage;
import com.bookwheel.server.community.repository.PostCommentRepository;
import com.bookwheel.server.community.repository.PostLikeRepository;
import com.bookwheel.server.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostDeletionService {

    private final PostCommentRepository postCommentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final S3Service s3Service;

    @Transactional
    public void delete(Post post) {
        List<String> imageObjectKeys = post.getImages().stream()
            .map(PostImage::getObjectKey)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

        postCommentRepository.deleteByPost(post);
        postLikeRepository.deleteByPost(post);
        postRepository.delete(post);
        registerPostCommitImageCleanup(imageObjectKeys);
    }

    private void registerPostCommitImageCleanup(List<String> objectKeys) {
        if (objectKeys.isEmpty()) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            objectKeys.forEach(s3Service::deleteObject);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectKeys.forEach(s3Service::deleteObject);
            }
        });
    }
}
