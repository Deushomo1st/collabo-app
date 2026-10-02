package com.collabo.backend.repository;

import com.collabo.backend.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    boolean existsByFollowerIdAndFollowedId(UUID followerId, UUID followedId);
    long countByFollowedId(UUID followedId);
    long countByFollowerId(UUID followerId);
    void deleteByFollowerIdOrFollowedId(UUID followerId, UUID followedId);
    void deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);
    List<Follow> findTop50ByFollowedIdOrderByCreatedAtDesc(UUID followedId);
    List<Follow> findTop50ByFollowerIdOrderByCreatedAtDesc(UUID followerId);
    List<Follow> findTop500ByFollowedIdOrderByCreatedAtDesc(UUID followedId);
    List<Follow> findTop500ByFollowerIdOrderByCreatedAtDesc(UUID followerId);

    /** Everyone this user follows plus everyone who follows them. */
    @Query("select f.followedId from Follow f where f.followerId = :u union select f.followerId from Follow f where f.followedId = :u")
    List<UUID> networkOf(@Param("u") UUID userId);
}
