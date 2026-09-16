package com.bookwheel.server.admin.repository;

import com.bookwheel.server.community.entity.PostImage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    // 썸네일이 없는 이미지를 id 순서로 훑는다. 처리한 id 를 커서로 넘겨 다음 묶음을 가져온다.
    // 생성에 실패해 thumbnail_key 가 계속 비는 행이 있어도 커서가 전진하므로 같은 행을 다시 집지 않는다.
    List<PostImage> findByThumbnailKeyIsNullAndPostImageIdGreaterThan(Long postImageId, Pageable pageable);
}
