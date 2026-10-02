package com.collabo.backend.repository;

import com.collabo.backend.entity.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostViewRepository extends JpaRepository<PostView, UUID> {
    void deleteByPostId(UUID postId);

    /** Which of these posts the user has already been counted on. */
    @Query("select v.postId from PostView v where v.userId = :u and v.postId in :ids")
    List<UUID> seenAmong(@Param("u") UUID userId, @Param("ids") Collection<UUID> postIds);
}
