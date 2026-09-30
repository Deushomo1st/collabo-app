package com.collabo.backend.repository;

import com.collabo.backend.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    boolean existsByFollowerIdAndFollowedId(UUID followerId, UUID followedId);
    long countByFollowedId(UUID followedId);
    long countByFollowerId(UUID followerId);
    void deleteByFollowerIdAndFollowedId(UUID followerId, UUID followedId);
    List<Follow> findTop50ByFollowedIdOrderByCreatedAtDesc(UUID followedId);
    List<Follow> findTop50ByFollowerIdOrderByCreatedAtDesc(UUID followerId);
}
