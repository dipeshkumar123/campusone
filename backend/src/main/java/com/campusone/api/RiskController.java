package com.campusone.api;

import com.campusone.api.dto.RiskDtos;
import com.campusone.service.RiskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
public class RiskController {
    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping
    public RiskDtos.PageView page(@RequestParam(required = false) String status) {
        return riskService.page(status);
    }

    @PostMapping("/recompute")
    public RiskDtos.RecomputeResult recompute() {
        return riskService.recompute();
    }

    @PostMapping("/flags/{flagId}/mentor-task")
    public RiskDtos.TaskResponse createTask(@PathVariable Long flagId,
                                            @Valid @RequestBody RiskDtos.CreateTaskRequest request) {
        return riskService.createTask(flagId, request);
    }

    @PostMapping("/tasks/{taskId}/complete")
    public RiskDtos.TaskResponse completeTask(@PathVariable Long taskId) {
        return riskService.completeTask(taskId);
    }
}
