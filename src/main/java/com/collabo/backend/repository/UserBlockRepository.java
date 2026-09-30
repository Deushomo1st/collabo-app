package com.collabo.backend.repository;

import com.collabo.backend.entity.UserBlock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(UUID blockerId);
    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    /** Everyone with a block to or from this user, in either direction. */
    @Query("select b.blockedId from UserBlock b where b.blockerId = :u union select b.blockerId from UserBlock b where b.blockedId = :u")
    List<UUID> counterpartsOf(@Param("u") UUID userId);
}
