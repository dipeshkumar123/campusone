package com.campusone.api;

import com.campusone.api.dto.StudentDtos;
import com.campusone.service.StudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/students")
public class StudentController {
    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @GetMapping
    public StudentDtos.ListView list(@RequestParam(required = false) String query,
                                     @RequestParam(required = false) Long programmeId,
                                     @RequestParam(required = false) Integer year) {
        return studentService.list(query, programmeId, year);
    }

    @GetMapping("/{studentId}")
    public StudentDtos.Profile profile(@PathVariable Long studentId) {
        return studentService.profile(studentId);
    }
}
