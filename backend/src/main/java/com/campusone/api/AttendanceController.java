package com.campusone.api;

import com.campusone.api.dto.AttendanceDtos;
import com.campusone.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @GetMapping("/my-classes")
    public AttendanceDtos.MyClassesView myClasses(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.myClasses(date);
    }

    @GetMapping("/roster")
    public AttendanceDtos.RosterView roster(
            @RequestParam Long subjectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam int period) {
        return attendanceService.roster(subjectId, date, period);
    }

    @PostMapping("/sync")
    public AttendanceDtos.SyncResponse sync(@Valid @RequestBody AttendanceDtos.SyncRequest request) {
        return attendanceService.sync(request);
    }
}
