package com.bookwheel.server.admin.repository;

import com.bookwheel.server.community.entity.PostImage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    // 게시물별 최소 이미지 ID가 대표 이미지다. 대표 이미지에 썸네일이 없을 때만 조회한다.
    // 생성에 실패해 thumbnail_key 가 계속 비는 행이 있어도 커서가 전진하므로 같은 행을 다시 집지 않는다.
    @Query("""
            select image from PostImage image
            where image.thumbnailKey is null
              and image.postImageId > :postImageId
              and image.postImageId = (
                  select min(candidate.postImageId) from PostImage candidate
                  where candidate.post = image.post
              )
            """)
    List<PostImage> findRepresentativesMissingThumbnailAfter(
            @Param("postImageId") Long postImageId, Pageable pageable);
}
