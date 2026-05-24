package com.bookwheel.server.community.repository;

import com.bookwheel.server.common.cursor.GalleryCursor;
import com.bookwheel.server.community.entity.Post;

import java.util.List;

public interface PostRepositoryCustom {

    List<Post> findGalleryPage(GalleryCursor cursor, int limit);

    long countGalleryPosts();
}
