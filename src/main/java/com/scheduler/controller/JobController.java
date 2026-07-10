package com.scheduler.controller;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepo;

    @PostMapping
    public ResponseEntity<Job> submitJob(@RequestBody Job job) {
        job.setStatus(JobStatus.PENDING);
        // Set initial execution to NOW to ensure immediate first run
        job.setNextRunTime(LocalDateTime.now());
        
        // Just validate the cron expression if provided
        if (job.getCronExpression() != null && !job.getCronExpression().trim().isEmpty()) {
            try {
                org.springframework.scheduling.support.CronExpression.parse(job.getCronExpression());
            } catch (Exception e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(jobRepo.save(job));
    }

    @GetMapping
    public List<Job> getAllJobs() {
        return jobRepo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) {
        return jobRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelJob(@PathVariable String id) {
        jobRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
