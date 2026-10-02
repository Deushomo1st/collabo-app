package com.collabo.backend.repository;

import com.collabo.backend.entity.PostDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostDraftRepository extends JpaRepository<PostDraft, UUID> {
    List<PostDraft> findByAuthorIdOrderByUpdatedAtDesc(UUID authorId);
    long countByAuthorId(UUID authorId);
    void deleteByAuthorId(UUID authorId);
}
