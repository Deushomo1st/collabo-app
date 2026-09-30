package com.collabo.backend.repository;

import com.collabo.backend.entity.ThreadMember;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreadMemberRepository extends JpaRepository<ThreadMember, UUID> {
    Optional<ThreadMember> findByThreadIdAndUserId(UUID threadId, UUID userId);
    List<ThreadMember> findByUserId(UUID userId);
    List<ThreadMember> findByThreadId(UUID threadId);
    List<ThreadMember> findByThreadIdIn(Collection<UUID> threadIds);
}
