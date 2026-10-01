package com.collabo.backend.repository;

import com.collabo.backend.entity.CaseVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaseVoteRepository extends JpaRepository<CaseVote, UUID> {
    List<CaseVote> findByCaseId(UUID caseId);
    List<CaseVote> findByCaseIdIn(Collection<UUID> caseIds);
    Optional<CaseVote> findByCaseIdAndVoterId(UUID caseId, UUID voterId);
}
