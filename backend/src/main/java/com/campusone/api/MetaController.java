package com.campusone.api;

import com.campusone.api.dto.MetaContext;
import com.campusone.service.MetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class MetaController {
    private final MetaService metaService;

    public MetaController(MetaService metaService) {
        this.metaService = metaService;
    }

    @GetMapping("/context")
    public MetaContext context() {
        return metaService.context();
    }
}
