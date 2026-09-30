package com.collabo.backend.repository;

import com.collabo.backend.entity.CredentialEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CredentialEntryRepository extends JpaRepository<CredentialEntry, UUID> {
    List<CredentialEntry> findByUserIdOrderByOccurredAtDesc(UUID userId);
    boolean existsBySourceTypeAndSourceIdAndUserId(String sourceType, String sourceId, UUID userId);
}
