package com.scheduler.controller;

import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Controller", description = "Endpoints for managing distributed jobs")
public class JobController {

    private final JobRepository jobRepo;

    @Operation(summary = "Submit a new job", description = "Submits a new job to the distributed scheduler. Supports optional cron expressions.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job submitted successfully", 
                         content = @Content(schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "400", description = "Invalid cron expression provided")
    })
    @PostMapping
    public ResponseEntity<Job> submitJob(
            @Parameter(description = "Job payload to submit. Includes name, priority, and optional cron expression.", required = true)
            @RequestBody Job job) {
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

    @Operation(summary = "Retrieve all jobs", description = "Fetches a list of all jobs currently registered in the database, including their execution status.")
    @GetMapping
    public List<Job> getAllJobs() {
        return jobRepo.findAll();
    }

    @Operation(summary = "Get a job by ID", description = "Fetches the details of a single job by its unique identifier.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Job found", 
                         content = @Content(schema = @Schema(implementation = Job.class))),
            @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(
            @Parameter(description = "Unique ID of the job", required = true)
            @PathVariable String id) {
        return jobRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Cancel a job", description = "Deletes a job from the database, effectively canceling it if it's not currently running.")
    @ApiResponse(responseCode = "204", description = "Job successfully deleted or didn't exist")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelJob(
            @Parameter(description = "Unique ID of the job to cancel", required = true)
            @PathVariable String id) {
        jobRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
