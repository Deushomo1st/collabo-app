package com.collabo.backend.service;

import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.SpaceMemberDtos.MemberResponse;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.CollaboratorRepository;
import com.collabo.backend.repository.SpaceMemberRepository;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Who is in a space: joining, leaving, the list, role titles and permissions. */
@Service
@Transactional
public class SpaceMemberService {

    static final int MAX_TITLE = 40;
    private static final String GONE = "No such space.";

    private final SpaceRepository spaces;
    private final SpaceMemberRepository members;
    private final UserRepository users;
    private final SpaceService spaceAccess;
    private final CredentialService credentials;
    private final CollaboratorRepository collaborators;
    private final SpaceThreadService spaceThreads;

    public SpaceMemberService(SpaceRepository spaces, SpaceMemberRepository members, UserRepository users,
                              SpaceService spaceAccess, CredentialService credentials,
                              CollaboratorRepository collaborators, SpaceThreadService spaceThreads) {
        this.spaceThreads = spaceThreads; this.collaborators = collaborators;
        this.spaces = spaces; this.members = members; this.users = users; this.spaceAccess = spaceAccess; this.credentials = credentials;
    }

    /** A deliberate act by an accepted applicant. Joining again does nothing; a removed person cannot. */
    public void join(User me, UUID spaceId) {
        Space s = find(spaceId);
        String role = spaceAccess.roleOf(me, s);
        if (role == null) throw new ResourceNotFoundException(GONE);
        if (role.equals("OWNER")) throw new InvalidProfileException("You already own this space.");
        if (isCollaborator(me, s)) throw new InvalidProfileException("You are already in as a collaborator.");
        Optional<SpaceMember> existing = members.findBySpaceIdAndUserId(s.getId(), me.getId());
        if (existing.isPresent()) {
            SpaceMember m = existing.get();
            if (m.getState() == SpaceMember.State.REMOVED) throw new InvalidProfileException("You were removed from this space.");
            if (m.getState() == SpaceMember.State.LEFT) { m.rejoin(); members.save(m); }
        } else {
            members.save(new SpaceMember(s.getId(), me.getId()));
        }
        spaceThreads.joinWorkspace(s, me.getId());
        credentials.record(me.getId(), CredentialKind.SPACE_FORMED, s.getName(), "", "space", s.getId().toString(), null);   // once per space
    }

    public void leave(User me, UUID spaceId) {
        Space s = find(spaceId);
        if (s.getOwnerId().equals(me.getId())) throw new InvalidProfileException("The owner cannot leave their own space.");
        if (isCollaborator(me, s)) throw new InvalidProfileException("Step down as a collaborator instead.");
        SpaceMember m = members.findBySpaceIdAndUserId(s.getId(), me.getId()).filter(x -> x.getState() == SpaceMember.State.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(GONE));
        m.setState(SpaceMember.State.LEFT);
        members.save(m);
        spaceThreads.leaveWorkspace(s, me.getId());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(User me, UUID spaceId) {
        Space s = find(spaceId);
        if (spaceAccess.roleOf(me, s) == null) throw new ResourceNotFoundException(GONE);
        List<MemberResponse> out = new ArrayList<>();
        for (SpaceMember m : members.findBySpaceIdAndStateOrderByJoinedAtAsc(s.getId(), SpaceMember.State.ACTIVE)) {
            users.findById(m.getUserId()).ifPresent(u -> out.add(present(s, u, m)));
        }
        return out;
    }

    /** The owner or a collaborator. The owner's own row is fixed. Weighty permissions need confirm=true. */
    public MemberResponse update(User me, UUID spaceId, String username, String title, List<String> requested, Boolean confirm) {
        Space s = find(spaceId);
        if (!s.getOwnerId().equals(me.getId()) && !isCollaborator(me, s)) throw new ResourceNotFoundException(GONE);
        User target = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No such member."));
        if (target.getId().equals(s.getOwnerId())) throw new InvalidProfileException("The owner's role is fixed.");
        SpaceMember m = activeMember(s, target);
        if (title != null) {
            String t = title.trim();
            if (t.length() > MAX_TITLE) throw new InvalidProfileException("Keep the role title under " + MAX_TITLE + " characters.");
            m.setTitle(t);
        }
        if (requested != null) {
            Set<SpacePermission> next = EnumSet.noneOf(SpacePermission.class);
            for (String r : requested) next.add(parse(r));
            boolean newWeighty = next.stream().anyMatch(p -> p.isWeighty() && !m.getPermissions().contains(p));
            if (newWeighty && !Boolean.TRUE.equals(confirm)) {
                throw new InvalidProfileException("These permissions let this person change the team itself. Confirm to grant them.");
            }
            m.setPermissions(next);
        }
        return present(s, target, members.save(m));
    }

    /** The owner, or a member holding ACCEPT_MEMBERS, removes someone. The owner cannot be removed. */
    public void remove(User me, UUID spaceId, String username) {
        Space s = find(spaceId);
        boolean allowed = s.getOwnerId().equals(me.getId()) || members.findBySpaceIdAndUserId(s.getId(), me.getId())
                .filter(x -> x.getState() == SpaceMember.State.ACTIVE && x.getPermissions().contains(SpacePermission.ACCEPT_MEMBERS)).isPresent();
        if (!allowed) throw new ResourceNotFoundException(GONE);
        User target = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No such member."));
        if (target.getId().equals(s.getOwnerId())) throw new InvalidProfileException("The owner cannot be removed.");
        SpaceMember m = activeMember(s, target);
        m.setState(SpaceMember.State.REMOVED);
        m.setPermissions(EnumSet.noneOf(SpacePermission.class));
        members.save(m);
        spaceThreads.leaveWorkspace(s, target.getId());
    }

    private boolean isCollaborator(User me, Space s) {
        return collaborators.existsByPostIdAndUserIdAndState(s.getPostId(), me.getId(), Collaborator.State.ACTIVE);
    }

    private Space find(UUID id) { return spaces.findById(id).orElseThrow(() -> new ResourceNotFoundException(GONE)); }

    private SpaceMember activeMember(Space s, User u) {
        return members.findBySpaceIdAndUserId(s.getId(), u.getId()).filter(x -> x.getState() == SpaceMember.State.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No such member."));
    }

    private static MemberResponse present(Space s, User u, SpaceMember m) {
        return new MemberResponse(PersonDto.of(u), u.getId().equals(s.getOwnerId()), m.getTitle(), names(m.getPermissions()), m.getJoinedAt());
    }

    private static SpacePermission parse(String name) {
        try { return SpacePermission.valueOf(name); }
        catch (RuntimeException e) { throw new InvalidProfileException("Unknown permission: " + name); }
    }

    private static List<String> names(Set<SpacePermission> set) { return set.stream().map(Enum::name).sorted().toList(); }
}
