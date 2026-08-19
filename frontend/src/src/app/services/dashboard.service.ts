import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin } from 'rxjs';
import { DashboardStats, WorkerNode, Job, CreateJobRequest } from '../models/dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  private readonly apiUrl = '/api';

  constructor(private http: HttpClient) {}

  getStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.apiUrl}/dashboard`);
  }

  getNodes(): Observable<WorkerNode[]> {
    return this.http.get<WorkerNode[]>(`${this.apiUrl}/nodes`);
  }

  getJobs(): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.apiUrl}/jobs`);
  }

  getDashboardData(): Observable<{ stats: DashboardStats; nodes: WorkerNode[]; jobs: Job[] }> {
    return forkJoin({
      stats: this.getStats(),
      nodes: this.getNodes(),
      jobs: this.getJobs()
    });
  }

  createJob(jobData: CreateJobRequest): Observable<Job> {
    return this.http.post<Job>(`${this.apiUrl}/jobs`, jobData);
  }
}
