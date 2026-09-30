package com.collabo.backend.repository;

import com.collabo.backend.entity.ProfileLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileLinkRepository extends JpaRepository<ProfileLink, UUID> {

    List<ProfileLink> findByUserIdOrderByPositionAsc(UUID userId);

    void deleteByUserId(UUID userId);
}
