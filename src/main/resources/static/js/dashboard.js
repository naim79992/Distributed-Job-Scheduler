async function updateDashboard() {
    try {
        // Fetch stats
        const statsResponse = await fetch('/api/dashboard');
        const stats = await statsResponse.json();
        document.getElementById('stat-total').innerText = stats.totalJobs;
        document.getElementById('stat-running').innerText = stats.runningJobs;
        document.getElementById('stat-completed').innerText = stats.completedJobs;
        document.getElementById('stat-nodes').innerText = stats.aliveNodes;

        // Fetch nodes
        const nodesResponse = await fetch('/api/nodes');
        const nodes = await nodesResponse.json();
        const nodeBody = document.getElementById('node-table-body');
        nodeBody.innerHTML = nodes.map(node => `
            <tr>
                <td>
                    ${node.nodeId}
                    ${node.leader ? '<span class="leader-indicator">LEADER</span>' : ''}
                </td>
                <td>${node.host}:${node.port}</td>
                <td class="${node.status === 'ALIVE' ? 'status-alive' : 'status-dead'}">
                    ● ${node.status}
                </td>
            </tr>
        `).join('');

        // Fetch jobs
        const jobsResponse = await fetch('/api/jobs');
        const jobs = await jobsResponse.json();
        const jobBody = document.getElementById('job-table-body');
        
        const formatDate = (dateString) => {
            if (!dateString) return '-';
            const d = new Date(dateString);
            return d.toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        };

        // Remove .slice(-10) to show all jobs
        jobBody.innerHTML = jobs.reverse().map(job => `
            <tr>
                <td>${job.name}</td>
                <td><span class="status-badge status-${job.status.toLowerCase()}">${job.status}</span></td>
                <td style="font-size: 0.75rem; color: var(--text-secondary)">${job.workerNodeId || '-'}</td>
                <td style="font-size: 0.75rem; color: var(--text-secondary)">${formatDate(job.lockedAt)}</td>
                <td style="font-size: 0.75rem; color: var(--text-secondary)">${formatDate(job.lastRunTime)}</td>
                <td style="font-size: 0.75rem; color: var(--text-secondary)">${formatDate(job.nextRunTime)}</td>
            </tr>
        `).join('');

    } catch (error) {
        console.error('Failed to update dashboard', error);
    }
}

document.getElementById('create-job-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button');
    const originalText = btn.innerText;
    btn.innerText = 'Adding...';

    const jobData = {
        name: document.getElementById('job-name').value,
        cron: document.getElementById('job-cron').value,
        priority: parseInt(document.getElementById('job-priority').value)
    };

    try {
        const response = await fetch('/api/jobs', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(jobData)
        });
        
        if (response.ok) {
            document.getElementById('create-job-form').reset();
            updateDashboard(); // immediately show the new job
        } else {
            alert('Failed to add job.');
        }
    } catch (err) {
        console.error('Error adding job', err);
    } finally {
        btn.innerText = originalText;
    }
});

setInterval(updateDashboard, 5000);
updateDashboard();
