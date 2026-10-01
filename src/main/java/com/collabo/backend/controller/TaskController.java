package com.collabo.backend.controller;

import com.collabo.backend.config.CurrentUser;
import com.collabo.backend.dto.TaskDtos.CreateRequest;
import com.collabo.backend.dto.TaskDtos.TaskResponse;
import com.collabo.backend.dto.TaskDtos.UpdateRequest;
import com.collabo.backend.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** A Workspace's tasks. Thin shell over TaskService. */
@RestController
@RequestMapping("/api/spaces/{spaceId}/tasks")
public class TaskController {

    private final TaskService tasks;
    private final CurrentUser current;

    public TaskController(TaskService tasks, CurrentUser current) { this.tasks = tasks; this.current = current; }

    @GetMapping
    public List<TaskResponse> list(@PathVariable UUID spaceId) { return tasks.list(current.require(), spaceId); }

    @PostMapping
    public TaskResponse create(@PathVariable UUID spaceId, @RequestBody CreateRequest req) {
        return tasks.create(current.require(), spaceId, req.title(), req.assignee());
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@PathVariable UUID spaceId, @PathVariable UUID id, @RequestBody UpdateRequest req) {
        return tasks.update(current.require(), spaceId, id, req.status(), req.assignee(), Boolean.TRUE.equals(req.unassign()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID spaceId, @PathVariable UUID id) { tasks.delete(current.require(), spaceId, id); }
}
