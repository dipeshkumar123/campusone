package com.campusone.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class AssistantDtos {
    private AssistantDtos() {
    }

    public record QuestionRequest(
            @NotBlank(message = "Ask a question first.")
            @Size(min = 4, max = 300, message = "Question must be between 4 and 300 characters.")
            String question
    ) {
    }

    public record FilterChip(String label, String value) {
    }

    public record Column(String key, String label, String type) {
    }

    public record Answer(
            String status,
            String summary,
            String interpretation,
            List<FilterChip> filters,
            int rowCount,
            List<Column> columns,
            List<Map<String, Object>> rows,
            String plan,
            String scopeNote,
            List<String> examples,
            List<String> clarificationOptions,
            boolean readOnly,
            String generatedAt
    ) {
    }
}
