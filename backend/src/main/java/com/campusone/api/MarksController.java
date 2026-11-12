package com.campusone.api;

import com.campusone.api.dto.MarksDtos;
import com.campusone.service.MarksService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/marks")
public class MarksController {
    private final MarksService marksService;

    public MarksController(MarksService marksService) {
        this.marksService = marksService;
    }

    @GetMapping("/subjects")
    public List<MarksDtos.SubjectCard> subjects() {
        return marksService.subjects();
    }

    @GetMapping("/{subjectId}")
    public MarksDtos.Sheet sheet(@PathVariable Long subjectId) {
        return marksService.sheet(subjectId);
    }

    @PutMapping("/{subjectId}/draft")
    public MarksDtos.Sheet saveDraft(@PathVariable Long subjectId,
                                     @Valid @RequestBody MarksDtos.SaveRequest request) {
        return marksService.saveDraft(subjectId, request);
    }

    @PostMapping("/{subjectId}/lock")
    public MarksDtos.Sheet lock(@PathVariable Long subjectId,
                                @RequestBody(required = false) MarksDtos.LockRequest request) {
        return marksService.lock(subjectId, request);
    }

    @PostMapping("/{subjectId}/unlock")
    public MarksDtos.Sheet unlock(@PathVariable Long subjectId,
                                  @Valid @RequestBody MarksDtos.UnlockRequest request) {
        return marksService.unlock(subjectId, request);
    }

    @GetMapping("/{subjectId}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long subjectId) {
        MarksDtos.Sheet sheet = marksService.sheet(subjectId);
        byte[] csv = marksService.exportCsv(subjectId).getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("internal-marks-" + sheet.subject().code() + ".csv")
                .build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }
}
