package com.collabo.backend.repository;

import com.collabo.backend.entity.UserBlock;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
