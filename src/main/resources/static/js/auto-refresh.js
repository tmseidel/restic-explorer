/**
 * Auto-refresh module for Restic Explorer.
 *
 * Polls /api/status at a configurable interval and reloads the page
 * when the status data changes (e.g. a scan completes or starts).
 * Also updates the active-jobs indicator in the navbar.
 *
 * Usage: include this script on any page that should auto-refresh.
 * Set data-repo-id="<id>" on the <script> tag to limit refresh
 * checks to a single repository (used on the snapshots page).
 */
(function () {
    'use strict';

    var POLL_INTERVAL = 10000; // 10 seconds
    var lastHash = null;
    var scriptTag = document.currentScript;
    var repoId = scriptTag ? scriptTag.getAttribute('data-repo-id') : null;

    function computeHash(data) {
        return JSON.stringify(data.repositories) + '|' + data.overallStatus;
    }

    function updateActiveJobsBadge(jobs) {
        var badge = document.getElementById('active-jobs-badge');
        var container = document.getElementById('active-jobs-container');
        if (!badge || !container) {
            return;
        }

        if (jobs.length === 0) {
            container.style.display = 'none';
            return;
        }

        container.style.display = '';
        badge.textContent = jobs.length;

        var list = document.getElementById('active-jobs-list');
        if (list) {
            list.innerHTML = '';
            jobs.forEach(function (job) {
                var li = document.createElement('li');
                var icon = job.type === 'SCAN'
                    ? '<i class="bi bi-arrow-clockwise text-warning"></i> '
                    : '<i class="bi bi-shield-check text-info"></i> ';
                li.innerHTML = '<span class="dropdown-item-text">' + icon +
                    '<strong>' + escapeHtml(job.repositoryName) + '</strong> — ' +
                    escapeHtml(job.type.toLowerCase()) + '</span>';
                list.appendChild(li);
            });
        }
    }

    function escapeHtml(text) {
        var div = document.createElement('div');
        div.appendChild(document.createTextNode(text));
        return div.innerHTML;
    }

    function checkStatus() {
        fetch('/api/status')
            .then(function (response) {
                if (!response.ok) { throw new Error('Status check failed'); }
                return response.json();
            })
            .then(function (data) {
                var currentHash;

                if (repoId) {
                    // On snapshots page: only watch the specific repository
                    var repoStatus = data.repositories[repoId];
                    currentHash = repoStatus ? JSON.stringify(repoStatus) : '';
                } else {
                    currentHash = computeHash(data);
                }

                if (lastHash !== null && lastHash !== currentHash) {
                    location.reload();
                    return;
                }
                lastHash = currentHash;

                updateActiveJobsBadge(data.activeJobs || []);
            })
            .catch(function () {
                // Silently ignore errors — will retry on next interval
            });
    }

    // Initial check to capture baseline state
    checkStatus();
    setInterval(checkStatus, POLL_INTERVAL);
}());
