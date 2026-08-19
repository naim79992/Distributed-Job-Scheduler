import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subject, timer } from 'rxjs';
import { switchMap, takeUntil } from 'rxjs/operators';
import { DashboardService } from '../../services/dashboard.service';
import { DashboardStats, WorkerNode, Job } from '../../models/dashboard.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  stats: DashboardStats = {
    totalJobs: 0,
    runningJobs: 0,
    completedJobs: 0,
    aliveNodes: 0
  };
  nodes: WorkerNode[] = [];
  jobs: Job[] = [];

  jobForm: FormGroup;
  isSubmitting = false;
  submitError: string | null = null;
  submitSuccess: string | null = null;
  isLoading = true;
  lastUpdated: Date | null = null;

  private destroy$ = new Subject<void>();

  constructor(
    private dashboardService: DashboardService,
    private fb: FormBuilder
  ) {
    this.jobForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2)]],
      cron: [''],
      priority: [1, [Validators.required, Validators.min(1), Validators.max(10)]]
    });
  }

  ngOnInit(): void {
    // Auto refresh every 5 seconds
    timer(0, 5000)
      .pipe(
        switchMap(() => this.dashboardService.getDashboardData()),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (data: { stats: DashboardStats; nodes: WorkerNode[]; jobs: Job[] }) => {
          this.stats = data.stats;
          this.nodes = data.nodes;
          // Reverse jobs to display latest first
          this.jobs = [...data.jobs].reverse();
          this.isLoading = false;
          this.lastUpdated = new Date();
        },
        error: (err: unknown) => {
          console.error('Error fetching dashboard data:', err);
          this.isLoading = false;
        }
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  refreshNow(): void {
    this.dashboardService.getDashboardData().subscribe({
      next: (data: { stats: DashboardStats; nodes: WorkerNode[]; jobs: Job[] }) => {
        this.stats = data.stats;
        this.nodes = data.nodes;
        this.jobs = [...data.jobs].reverse();
        this.lastUpdated = new Date();
      },
      error: (err: unknown) => console.error('Failed manual refresh', err)
    });
  }

  onSubmit(): void {
    if (this.jobForm.invalid) {
      this.jobForm.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.submitError = null;
    this.submitSuccess = null;

    const formValues = this.jobForm.value;

    this.dashboardService.createJob({
      name: formValues.name.trim(),
      cron: formValues.cron ? formValues.cron.trim() : undefined,
      priority: Number(formValues.priority)
    }).subscribe({
      next: () => {
        this.isSubmitting = false;
        this.submitSuccess = 'Job created successfully!';
        this.jobForm.reset({ name: '', cron: '', priority: 1 });
        this.refreshNow();
        setTimeout(() => (this.submitSuccess = null), 4000);
      },
      error: (err: unknown) => {
        console.error('Error adding job:', err);
        this.isSubmitting = false;
        this.submitError = 'Failed to create job. Please try again.';
      }
    });
  }

  formatDate(dateString: string | null | undefined): string {
    if (!dateString) return '-';
    const d = new Date(dateString);
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  getStatusClass(status: string): string {
    const s = (status || '').toLowerCase();
    switch (s) {
      case 'running': return 'status-running';
      case 'done':
      case 'completed': return 'status-done';
      case 'failed': return 'status-failed';
      default: return 'status-pending';
    }
  }
}
