package com.bookwheel.server.community.image;

import com.bookwheel.server.admin.repository.PostImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

// 생성이 끝난 썸네일 키만 짧은 트랜잭션으로 반영한다.
// MinIO 왕복은 이 트랜잭션 밖에서 이미 끝나 있어야 한다.
@Service
@RequiredArgsConstructor
public class PostThumbnailStore {

    private final PostImageRepository postImageRepository;

    @Transactional
    public void applyThumbnailKeys(Map<Long, String> thumbnailKeysByPostImageId) {
        if (thumbnailKeysByPostImageId.isEmpty()) {
            return;
        }

        postImageRepository.findAllById(thumbnailKeysByPostImageId.keySet())
                .forEach(image -> image.applyThumbnailKey(
                        thumbnailKeysByPostImageId.get(image.getPostImageId())));
    }
}
