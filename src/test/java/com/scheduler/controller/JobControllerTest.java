package com.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduler.entity.Job;
import com.scheduler.enums.JobStatus;
import com.scheduler.repository.JobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer slice test for JobController.
 * Uses @WebMvcTest to load only the web layer — fast and focused.
 */
@WebMvcTest(JobController.class)
@DisplayName("JobController")
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JobRepository jobRepo;

    // ── POST /api/jobs ──────────────────────────────────────────────────────────
    @Nested
    @DisplayName("POST /api/jobs")
    class SubmitJob {

        @Test
        @DisplayName("returns 200 and saved job when request is valid (one-time job)")
        void returns200_withValidOneTimeJob() throws Exception {
            Job request = Job.builder().name("Backup Job").priority(1).build();
            Job saved   = Job.builder().name("Backup Job").priority(1)
                    .status(JobStatus.PENDING)
                    .nextRunTime(LocalDateTime.now())
                    .build();

            given(jobRepo.save(any(Job.class))).willReturn(saved);

            mockMvc.perform(post("/api/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Backup Job"))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("returns 200 when a valid cron expression is provided")
        void returns200_withValidCronExpression() throws Exception {
            Job request = Job.builder().name("Cron Job").cronExpression("0 * * * * *").priority(2).build();
            Job saved   = Job.builder().name("Cron Job").cronExpression("0 * * * * *")
                    .status(JobStatus.PENDING).build();

            given(jobRepo.save(any(Job.class))).willReturn(saved);

            mockMvc.perform(post("/api/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Cron Job"));
        }

        @Test
        @DisplayName("returns 400 when cron expression is invalid")
        void returns400_withInvalidCronExpression() throws Exception {
            Job request = Job.builder().name("Bad Job").cronExpression("NOT_A_CRON").priority(1).build();

            mockMvc.perform(post("/api/jobs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/jobs ───────────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/jobs")
    class GetAllJobs {

        @Test
        @DisplayName("returns 200 with list of all jobs")
        void returns200_withJobList() throws Exception {
            Job job1 = Job.builder().name("Job A").status(JobStatus.PENDING).build();
            Job job2 = Job.builder().name("Job B").status(JobStatus.DONE).build();
            given(jobRepo.findAll()).willReturn(List.of(job1, job2));

            mockMvc.perform(get("/api/jobs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Job A"))
                    .andExpect(jsonPath("$[1].name").value("Job B"));
        }

        @Test
        @DisplayName("returns 200 with empty list when no jobs exist")
        void returns200_withEmptyList() throws Exception {
            given(jobRepo.findAll()).willReturn(List.of());

            mockMvc.perform(get("/api/jobs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    // ── GET /api/jobs/{id} ──────────────────────────────────────────────────────
    @Nested
    @DisplayName("GET /api/jobs/{id}")
    class GetJobById {

        @Test
        @DisplayName("returns 200 with job when found")
        void returns200_whenJobFound() throws Exception {
            Job job = Job.builder().name("My Job").status(JobStatus.RUNNING).build();
            given(jobRepo.findById("abc-123")).willReturn(Optional.of(job));

            mockMvc.perform(get("/api/jobs/abc-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("My Job"))
                    .andExpect(jsonPath("$.status").value("RUNNING"));
        }

        @Test
        @DisplayName("returns 404 when job not found")
        void returns404_whenJobNotFound() throws Exception {
            given(jobRepo.findById("non-existent")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/jobs/non-existent"))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /api/jobs/{id} ───────────────────────────────────────────────────
    @Nested
    @DisplayName("DELETE /api/jobs/{id}")
    class CancelJob {

        @Test
        @DisplayName("returns 204 and deletes the job")
        void returns204_andDeletesJob() throws Exception {
            mockMvc.perform(delete("/api/jobs/job-999"))
                    .andExpect(status().isNoContent());

            verify(jobRepo).deleteById("job-999");
        }
    }
}
