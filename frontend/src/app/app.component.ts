import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Subscription, interval } from 'rxjs';

interface Job {
  id: string;
  name: string;
  cronExpression?: string;
  priority: number;
  status: string;
  workerNodeId?: string;
  lockedAt?: string;
  lastRunTime?: string;
  nextRunTime?: string;
}

interface WorkerNode {
  nodeId: string;
  host: string;
  port: number;
  status: string;
  leader: boolean;
}

interface DashboardStats {
  totalJobs: number;
  runningJobs: number;
  completedJobs: number;
  failedJobs: number;
  deadJobs: number;
  aliveNodes: number;
  deadNodes: number;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  stats: DashboardStats = {
    totalJobs: 0,
    runningJobs: 0,
    completedJobs: 0,
    failedJobs: 0,
    deadJobs: 0,
    aliveNodes: 0,
    deadNodes: 0
  };

  nodes: WorkerNode[] = [];
  jobs: Job[] = [];

  // Form Model
  newJobName = '';
  newJobCron = '';
  newJobPriority = 5;
  isAdding = false;

  private refreshSub?: Subscription;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.fetchData();
    // Poll data every 5 seconds
    this.refreshSub = interval(5000).subscribe(() => this.fetchData());
  }

  ngOnDestroy() {
    this.refreshSub?.unsubscribe();
  }

  fetchData() {
    this.http.get<DashboardStats>('/api/dashboard').subscribe({
      next: (data) => this.stats = data,
      error: (err) => console.error('Failed to fetch dashboard stats', err)
    });

    this.http.get<WorkerNode[]>('/api/nodes').subscribe({
      next: (data) => this.nodes = data,
      error: (err) => console.error('Failed to fetch nodes', err)
    });

    this.http.get<Job[]>('/api/jobs').subscribe({
      next: (data) => {
        this.jobs = data.slice().reverse();
      },
      error: (err) => console.error('Failed to fetch jobs', err)
    });
  }

  addJob() {
    if (!this.newJobName.trim()) return;

    this.isAdding = true;
    const payload = {
      name: this.newJobName,
      cronExpression: this.newJobCron || null,
      priority: this.newJobPriority
    };

    this.http.post<Job>('/api/jobs', payload).subscribe({
      next: () => {
        this.newJobName = '';
        this.newJobCron = '';
        this.newJobPriority = 5;
        this.isAdding = false;
        this.fetchData();
      },
      error: (err) => {
        console.error('Failed to add job', err);
        alert('Failed to add job.');
        this.isAdding = false;
      }
    });
  }

  deleteJob(id: string) {
    if (confirm('Are you sure you want to delete/cancel this job?')) {
      this.http.delete(`/api/jobs/${id}`).subscribe({
        next: () => this.fetchData(),
        error: (err) => console.error('Failed to delete job', err)
      });
    }
  }

  formatDate(dateString?: string): string {
    if (!dateString) return '-';
    const d = new Date(dateString);
    return d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
}
