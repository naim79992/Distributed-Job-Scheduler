import { Component } from '@angular/core';
import { DashboardComponent } from './components/dashboard/dashboard.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [DashboardComponent],
  template: `<app-dashboard></app-dashboard>`,
  styles: [`
    :host {
      display: block;
      min-height: 100vh;
      background-color: #0f172a;
    }
  `]
})
export class AppComponent {
  title = 'distributed-job-scheduler-dashboard';
}
