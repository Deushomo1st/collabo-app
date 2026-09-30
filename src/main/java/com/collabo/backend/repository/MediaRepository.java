package com.collabo.backend.repository;

import com.collabo.backend.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    List<Media> findByPostIdIn(Collection<UUID> postIds);
    List<Media> findByPostId(UUID postId);
    List<Media> findByOwnerIdAndIdIn(UUID ownerId, Collection<UUID> ids);
    long countByOwnerIdAndPostIdIsNull(UUID ownerId);
}
