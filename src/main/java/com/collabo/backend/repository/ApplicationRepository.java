package com.collabo.backend.repository;

import com.collabo.backend.entity.Application;
import com.collabo.backend.entity.ApplicationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
    Optional<Application> findByPostIdAndApplicantId(UUID postId, UUID applicantId);
    List<Application> findByApplicantIdOrderByCreatedAtDesc(UUID applicantId);
    // ponytail: the whole stack in one go, fine for a founder's review list; page it if a post ever draws thousands
    List<Application> findByPostIdAndStateNotOrderByCreatedAtDesc(UUID postId, ApplicationState state);
    List<Application> findByPostIdAndStateNotOrderByCreatedAtAsc(UUID postId, ApplicationState state);

    /** [postId, count] for a page of posts, withdrawn ones left out. */
    @Query("select a.postId, count(a) from Application a where a.postId in :ids and a.state <> :withdrawn group by a.postId")
    List<Object[]> counts(@Param("ids") Collection<UUID> postIds, @Param("withdrawn") ApplicationState withdrawn);

    /** [postId, state] of the user's own applications among these posts, withdrawn ones left out. */
    @Query("select a.postId, a.state from Application a where a.applicantId = :u and a.postId in :ids and a.state <> :withdrawn")
    List<Object[]> statesOf(@Param("u") UUID applicantId, @Param("ids") Collection<UUID> postIds, @Param("withdrawn") ApplicationState withdrawn);

    /** Has this person a live (not withdrawn) application to any post by that author? */
    @Query("select count(a) > 0 from Application a, Post p where p.id = a.postId and a.applicantId = :applicant "
            + "and p.authorId = :author and a.state <> :withdrawn")
    boolean liveBetween(@Param("applicant") UUID applicantId, @Param("author") UUID authorId, @Param("withdrawn") ApplicationState withdrawn);

    boolean existsByPostIdAndState(UUID postId, ApplicationState state);

    void deleteByPostId(UUID postId);
}
