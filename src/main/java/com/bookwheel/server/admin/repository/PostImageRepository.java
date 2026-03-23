package com.bookwheel.server.admin.repository;

import com.bookwheel.server.community.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
}
