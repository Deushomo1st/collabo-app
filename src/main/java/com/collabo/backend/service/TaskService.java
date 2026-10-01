package com.collabo.backend.service;

import com.collabo.backend.dto.PersonDto;
import com.collabo.backend.dto.TaskDtos.TaskResponse;
import com.collabo.backend.entity.*;
import com.collabo.backend.exception.ForbiddenException;
import com.collabo.backend.exception.InvalidProfileException;
import com.collabo.backend.exception.ResourceNotFoundException;
import com.collabo.backend.repository.SpaceMemberRepository;
import com.collabo.backend.repository.SpaceRepository;
import com.collabo.backend.repository.SpaceTaskRepository;
import com.collabo.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A Workspace's tasks. The team (owner and joined members) sees and moves them; accepted applicants and outsiders never do. */
@Service
@Transactional
public class TaskService {

    static final int MAX_TITLE = 140;
    private static final String GONE = "No such space.";

    private final SpaceRepository spaces;
    private final SpaceTaskRepository tasks;
    private final SpaceMemberRepository members;
    private final UserRepository users;
    private final SpaceService spaceAccess;
    private final SpaceThreadService spaceThreads;

    public TaskService(SpaceRepository spaces, SpaceTaskRepository tasks, SpaceMemberRepository members, UserRepository users,
                       SpaceService spaceAccess, SpaceThreadService spaceThreads) {
        this.spaces = spaces; this.tasks = tasks; this.members = members; this.users = users; this.spaceAccess = spaceAccess; this.spaceThreads = spaceThreads;
    }

    public TaskResponse create(User me, UUID spaceId, String rawTitle, String assignee) {
        Space s = team(me, spaceId);
        String title = rawTitle == null ? "" : rawTitle.trim();
        if (title.isEmpty()) throw new InvalidProfileException("Give the task a title.");
        if (title.length() > MAX_TITLE) throw new InvalidProfileException("Keep the title under " + MAX_TITLE + " characters.");
        UUID who = assignee == null || assignee.isBlank() ? null : seated(s, assignee);
        SpaceTask t = tasks.save(new SpaceTask(s.getId(), title, me.getId(), who));
        spaceThreads.announce(s, me.getUsername() + " added a task: " + title + (who == null ? "" : " (for " + assignee + ")"));
        return one(t);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(User me, UUID spaceId) {
        Space s = team(me, spaceId);
        List<SpaceTask> rows = tasks.findBySpaceIdOrderByCreatedAtAsc(s.getId());
        Map<UUID, User> people = people(rows);
        return rows.stream().map(t -> present(t, people)).toList();
    }

    public TaskResponse update(User me, UUID spaceId, UUID taskId, String status, String assignee, boolean unassign) {
        Space s = team(me, spaceId);
        SpaceTask t = tasks.findByIdAndSpaceId(taskId, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such task."));
        if (status != null) {
            SpaceTask.Status next;
            try { next = SpaceTask.Status.valueOf(status); } catch (IllegalArgumentException e) { throw new InvalidProfileException("Unknown task status."); }
            boolean finishing = next == SpaceTask.Status.DONE && t.getStatus() != SpaceTask.Status.DONE;
            t.setStatus(next);
            if (finishing) spaceThreads.announce(s, me.getUsername() + " finished: " + t.getTitle());
        }
        if (unassign) t.setAssigneeId(null);
        else if (assignee != null && !assignee.isBlank()) t.setAssigneeId(seated(s, assignee));
        return one(tasks.save(t));
    }

    /** The one who added it, or the owner, can drop it. */
    public void delete(User me, UUID spaceId, UUID taskId) {
        Space s = team(me, spaceId);
        SpaceTask t = tasks.findByIdAndSpaceId(taskId, s.getId()).orElseThrow(() -> new ResourceNotFoundException("No such task."));
        if (!t.getCreatedBy().equals(me.getId()) && !s.getOwnerId().equals(me.getId())) throw new ForbiddenException("Only whoever added a task, or the owner, can remove it.");
        tasks.delete(t);
    }

    private Space team(User me, UUID spaceId) {
        Space s = spaces.findById(spaceId).orElseThrow(() -> new ResourceNotFoundException(GONE));
        String role = spaceAccess.roleOf(me, s);
        if (role == null || "APPLICANT".equals(role)) throw new ResourceNotFoundException(GONE);
        return s;
    }

    /** Someone can be given a task only while they are in the room. */
    private UUID seated(Space s, String username) {
        User u = users.findByUsername(username).orElseThrow(() -> new InvalidProfileException("Only someone in the room can be given a task."));
        boolean inRoom = members.findBySpaceIdAndUserId(s.getId(), u.getId()).filter(m -> m.getState() == SpaceMember.State.ACTIVE).isPresent();
        if (!inRoom) throw new InvalidProfileException("Only someone in the room can be given a task.");
        return u.getId();
    }

    private Map<UUID, User> people(List<SpaceTask> rows) {
        var ids = new java.util.HashSet<UUID>();
        rows.forEach(t -> { ids.add(t.getCreatedBy()); if (t.getAssigneeId() != null) ids.add(t.getAssigneeId()); });
        return users.findAllById(ids).stream().collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private TaskResponse one(SpaceTask t) { return present(t, people(List.of(t))); }

    private static TaskResponse present(SpaceTask t, Map<UUID, User> people) {
        User who = t.getAssigneeId() == null ? null : people.get(t.getAssigneeId());
        User by = people.get(t.getCreatedBy());
        return new TaskResponse(t.getId(), t.getTitle(), t.getStatus().name(), who == null ? null : PersonDto.of(who),
                by == null ? null : PersonDto.of(by), t.getCreatedAt(), t.getDoneAt());
    }
}
