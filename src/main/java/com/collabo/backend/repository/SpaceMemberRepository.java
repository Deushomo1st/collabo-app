package com.collabo.backend.repository;

import com.collabo.backend.entity.SpaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceMemberRepository extends JpaRepository<SpaceMember, UUID> {
    Optional<SpaceMember> findBySpaceIdAndUserId(UUID spaceId, UUID userId);
    List<SpaceMember> findBySpaceIdAndStateOrderByJoinedAtAsc(UUID spaceId, SpaceMember.State state);
}
