package com.scheduler.controller;

import com.scheduler.enums.JobStatus;
import com.scheduler.enums.NodeStatus;
import com.scheduler.repository.JobRepository;
import com.scheduler.repository.WorkerNodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class DashboardController {

    private final JobRepository jobRepo;
    private final WorkerNodeRepository nodeRepo;

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("jobs", jobRepo.findAll());
        model.addAttribute("nodes", nodeRepo.findAll());
        return "dashboard";
    }

    @GetMapping("/api/nodes")
    @ResponseBody
    public List<?> getNodes() {
        return nodeRepo.findAll();
    }

    @GetMapping("/api/dashboard")
    @ResponseBody
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalJobs", jobRepo.count());
        stats.put("runningJobs", jobRepo.findByStatusOrderByPriorityAsc(JobStatus.RUNNING).size());
        stats.put("completedJobs", jobRepo.findByStatusOrderByPriorityAsc(JobStatus.DONE).size());
        stats.put("failedJobs", jobRepo.findByStatusOrderByPriorityAsc(JobStatus.FAILED).size());
        stats.put("deadJobs", jobRepo.findByStatusOrderByPriorityAsc(JobStatus.DEAD).size());
        stats.put("aliveNodes", nodeRepo.findByStatus(NodeStatus.ALIVE).size());
        stats.put("deadNodes", nodeRepo.findByStatus(NodeStatus.DEAD).size());
        return stats;
    }
}
