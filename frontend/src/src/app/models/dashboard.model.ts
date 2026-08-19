export type NodeStatusType = 'ALIVE' | 'DEAD';
export type JobStatusType = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED' | 'DEAD';

export interface DashboardStats {
  totalJobs: number;
  runningJobs: number;
  completedJobs: number;
  failedJobs?: number;
  deadJobs?: number;
  aliveNodes: number;
  deadNodes?: number;
}

export interface WorkerNode {
  nodeId: string;
  host: string;
  port: number;
  status: NodeStatusType | string;
  leader: boolean;
}

export interface Job {
  id?: number | string;
  name: string;
  cron?: string;
  priority?: number;
  status: JobStatusType | string;
  workerNodeId?: string | null;
  lockedAt?: string | null;
  lastRunTime?: string | null;
  nextRunTime?: string | null;
}

export interface CreateJobRequest {
  name: string;
  cron?: string;
  priority: number;
}
