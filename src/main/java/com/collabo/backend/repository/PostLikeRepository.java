package com.collabo.backend.repository;

import com.collabo.backend.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostIdAndUserId(UUID postId, UUID userId);
    void deleteByPostId(UUID postId);

    /** [postId, count] rows for a page of posts. */
    @Query("select l.postId, count(l) from PostLike l where l.postId in :ids group by l.postId")
    List<Object[]> counts(@Param("ids") Collection<UUID> postIds);

    /** Which of these posts the user has liked. */
    @Query("select l.postId from PostLike l where l.userId = :u and l.postId in :ids")
    List<UUID> likedAmong(@Param("u") UUID userId, @Param("ids") Collection<UUID> postIds);
}
