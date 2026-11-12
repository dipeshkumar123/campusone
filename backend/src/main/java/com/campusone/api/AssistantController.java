package com.campusone.api;

import com.campusone.api.dto.AssistantDtos;
import com.campusone.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/query")
    @PreAuthorize("hasRole('ADMIN')")
    public AssistantDtos.Answer query(@Valid @RequestBody AssistantDtos.QuestionRequest request) {
        return assistantService.ask(request);
    }
}
