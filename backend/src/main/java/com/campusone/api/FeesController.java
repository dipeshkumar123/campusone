package com.campusone.api;

import com.campusone.api.dto.FeeDtos;
import com.campusone.service.FeeService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/fees")
public class FeesController {
    private final FeeService feeService;

    public FeesController(FeeService feeService) {
        this.feeService = feeService;
    }

    @GetMapping
    public FeeDtos.ListView list(@RequestParam(required = false) String query,
                                 @RequestParam(required = false) Long programmeId,
                                 @RequestParam(required = false) String status) {
        return feeService.list(query, programmeId, status);
    }

    @GetMapping("/{studentId}")
    public FeeDtos.FeeDetail detail(@PathVariable Long studentId) {
        return feeService.detail(studentId);
    }

    @PostMapping("/{studentId}/pay")
    public FeeDtos.PaymentView pay(@PathVariable Long studentId, @Valid @RequestBody FeeDtos.PayRequest request) {
        return feeService.pay(studentId, request);
    }

    @PostMapping("/reminders")
    public FeeDtos.ReminderResult sendReminders() {
        return feeService.sendReminders();
    }

    @GetMapping("/export/tally")
    public ResponseEntity<byte[]> tallyExport() {
        byte[] csv = feeService.tallyCsv().getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment().filename("tally-fee-export.csv").build();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(csv);
    }
}
