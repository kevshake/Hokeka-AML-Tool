// Case Management Enhanced Functions

let currentCaseId = null;

// Enhanced Case Fetching with new fields
window.fetchCases = async function() {
    try {
        const response = await fetch('compliance/cases', {
            credentials: 'include'
        });
        const cases = await response.json();
        
        const tbody = document.querySelector('#cases-table tbody');
        if (!tbody) return;
        
        tbody.innerHTML = cases.map(c => `
            <tr onclick="viewCaseDetail(${c.id})" style="cursor: pointer;">
                <td><span class="case-id">${c.caseReference || 'CASE-' + c.id}</span></td>
                <td>${c.merchantId || 'N/A'}</td>
                <td><span class="priority-badge ${c.priority?.toLowerCase() || 'medium'}">${c.priority || 'MEDIUM'}</span></td>
                <td><span class="status-badge ${getStatusClass(c.status)}">${c.status || 'NEW'}</span></td>
                <td>${c.assignedTo?.username || 'Unassigned'}</td>
                <td>${c.slaDeadline ? new Date(c.slaDeadline).toLocaleDateString() : 'N/A'}</td>
                <td>${c.daysOpen || 0} days</td>
                <td>
                    <button class="action-btn" onclick="event.stopPropagation(); viewCaseDetail(${c.id})">
                        <i class="fas fa-eye"></i>
                    </button>
                </td>
            </tr>
        `).join('');
        
        // Update stats
        updateCaseStats(cases);
    } catch (error) {
        console.error('Error fetching cases:', error);
        demoCases();
    }
};

function updateCaseStats(cases) {
    const openCases = cases.filter(c => ['NEW', 'ASSIGNED', 'IN_PROGRESS'].includes(c.status)).length;
    const overdue = cases.filter(c => {
        if (!c.slaDeadline) return false;
        return new Date(c.slaDeadline) < new Date() && c.status !== 'CLOSED_CLEARED';
    }).length;
    const escalated = cases.filter(c => c.escalated || c.status === 'ESCALATED').length;
    const unassigned = cases.filter(c => !c.assignedTo).length;
    
    document.getElementById('openCasesCount').textContent = openCases;
    document.getElementById('overdueCasesCount').textContent = overdue;
    document.getElementById('escalatedCasesCount').textContent = escalated;
    document.getElementById('unassignedCasesCount').textContent = unassigned;
}

// View Case Detail
window.viewCaseDetail = async function(caseId) {
    currentCaseId = caseId;
    if (typeof window.showView === 'function') {
        window.showView('case-detail-view');
    }
    
    try {
        const response = await fetch(`compliance/cases/${caseId}`, {
            credentials: 'include'
        });
        const caseData = await response.json();
        
        document.getElementById('case-detail-title').textContent = 
            `Case: ${caseData.caseReference || 'CASE-' + caseId}`;
        
        // Load case info
        document.getElementById('case-info-content').innerHTML = `
            <p><strong>Status:</strong> <span class="status-badge ${getStatusClass(caseData.status)}">${caseData.status}</span></p>
            <p><strong>Priority:</strong> <span class="priority-badge ${caseData.priority?.toLowerCase()}">${caseData.priority}</span></p>
            <p><strong>Assigned To:</strong> ${caseData.assignedTo?.username || 'Unassigned'}</p>
            <p><strong>SLA Deadline:</strong> ${caseData.slaDeadline ? new Date(caseData.slaDeadline).toLocaleString() : 'N/A'}</p>
            <p><strong>Days Open:</strong> ${caseData.daysOpen || 0}</p>
            <p><strong>Description:</strong> ${caseData.description || 'N/A'}</p>
        `;
        
        // Load timeline
        loadCaseTimeline(caseId);
        loadCaseActivities(caseId);
    } catch (error) {
        console.error('Error loading case details:', error);
    }
};

// Load Case Timeline (for case detail view)
async function loadCaseTimeline(caseId) {
    try {
        const response = await fetch(`cases/${caseId}/timeline`, {
            credentials: 'include'
        });
        const timeline = await response.json();
        
        const timelineContent = document.getElementById('case-timeline-content');
        if (timelineContent) {
            timelineContent.innerHTML = timeline.events.map(event => `
                <div class="timeline-event">
                    <div class="timeline-dot"></div>
                    <div class="timeline-content">
                        <strong>${event.type}</strong>
                        <p>${event.description}</p>
                        <small>${new Date(event.timestamp).toLocaleString()}</small>
                    </div>
                </div>
            `).join('');
        }
    } catch (error) {
        console.error('Error loading timeline:', error);
    }
}

// ==========================================================================
// CASE TIMELINE VIEW - Comprehensive Timeline Page
// ==========================================================================

// Initialize timeline view
window.initializeTimelineView = function() {
    const caseSelector = document.getElementById('timeline-case-selector');
    const filterSelector = document.getElementById('timeline-filter-type');
    
    if (caseSelector) {
        caseSelector.addEventListener('change', function() {
            const caseId = this.value;
            if (caseId) {
                loadCaseTimelineForView(caseId);
            } else {
                clearTimelineView();
            }
        });
    }
    
    if (filterSelector) {
        filterSelector.addEventListener('change', function() {
            const caseId = document.getElementById('timeline-case-selector')?.value;
            if (caseId) {
                loadCaseTimelineForView(caseId);
            }
        });
    }
};

// Load cases for timeline selector
window.loadTimelineCases = async function() {
    try {
        const response = await fetch('compliance/cases', {
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const cases = await response.json();
        
        const selector = document.getElementById('timeline-case-selector');
        if (!selector) return;
        
        // Clear existing options except the first one
        selector.innerHTML = '<option value="">Select a case...</option>';
        
        // Add cases to selector
        cases.forEach(caseItem => {
            const option = document.createElement('option');
            option.value = caseItem.id;
            option.textContent = `${caseItem.caseReference} - ${caseItem.description || 'No description'}`;
            selector.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading cases for timeline:', error);
    }
};

// Load timeline for the timeline view page
window.loadCaseTimelineForView = async function(caseId) {
    if (!caseId) return;
    
    try {
        const fetchOptions = {
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        };
        
        const response = await fetch(`cases/${caseId}/timeline`, fetchOptions);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const timeline = await response.json();
        
        // Load case details for info card
        const caseResponse = await fetch(`compliance/cases/${caseId}`, fetchOptions);
        let caseDetails = null;
        if (caseResponse.ok) {
            caseDetails = await caseResponse.json();
        }
        
        // Update case info card
        if (caseDetails) {
            updateTimelineCaseInfo(caseDetails);
        }
        
        // Get filter
        const filterType = document.getElementById('timeline-filter-type')?.value || '';
        
        // Filter events if needed
        let events = timeline.events || [];
        if (filterType) {
            events = events.filter(event => event.type === filterType);
        }
        
        // Render timeline
        renderTimeline(events, timeline.caseReference);
        
    } catch (error) {
        console.error('Error loading timeline:', error);
        const container = document.getElementById('timeline-container');
        if (container) {
            container.innerHTML = '<div class="timeline-error"><p>Error loading timeline. Please try again.</p></div>';
        }
    }
};

// Update case info card
function updateTimelineCaseInfo(caseDetails) {
    const caseInfo = document.getElementById('timeline-case-info');
    const caseReference = document.getElementById('timeline-case-reference');
    const caseDescription = document.getElementById('timeline-case-description');
    const caseStatus = document.getElementById('timeline-case-status');
    const casePriority = document.getElementById('timeline-case-priority');
    const caseAssigned = document.getElementById('timeline-case-assigned');
    
    if (caseInfo) caseInfo.style.display = 'block';
    if (caseReference) caseReference.textContent = caseDetails.caseReference || '-';
    if (caseDescription) caseDescription.textContent = caseDetails.description || 'No description';
    
    if (caseStatus) {
        const status = caseDetails.status || 'UNKNOWN';
        caseStatus.textContent = `Status: ${status}`;
        caseStatus.className = `case-status status-${status.toLowerCase()}`;
    }
    
    if (casePriority) {
        const priority = caseDetails.priority || 'MEDIUM';
        casePriority.textContent = `Priority: ${priority}`;
        casePriority.className = `case-priority priority-${priority.toLowerCase()}`;
    }
    
    if (caseAssigned) {
        const assignedTo = caseDetails.assignedTo ? 
            (caseDetails.assignedTo.username || caseDetails.assignedTo) : 'Unassigned';
        caseAssigned.textContent = `Assigned: ${assignedTo}`;
    }
}

// Render timeline events
function renderTimeline(events, caseReference) {
    const container = document.getElementById('timeline-container');
    if (!container) return;
    
    if (!events || events.length === 0) {
        container.innerHTML = '<div class="timeline-empty-state"><i class="fas fa-inbox"></i><p>No events found for this case</p></div>';
        return;
    }
    
    // Group events by date
    const eventsByDate = groupEventsByDate(events);
    
    let html = '<div class="timeline-wrapper-inner">';
    
    Object.keys(eventsByDate).sort().reverse().forEach(date => {
        html += `<div class="timeline-date-group">
            <div class="timeline-date-header">
                <h3>${formatDateHeader(date)}</h3>
                <span class="event-count">${eventsByDate[date].length} event(s)</span>
            </div>
            <div class="timeline-events-group">`;
        
        eventsByDate[date].forEach(event => {
            html += renderTimelineEvent(event);
        });
        
        html += '</div></div>';
    });
    
    html += '</div>';
    container.innerHTML = html;
}

// Group events by date
function groupEventsByDate(events) {
    const grouped = {};
    events.forEach(event => {
        const date = new Date(event.timestamp);
        const dateKey = date.toISOString().split('T')[0]; // YYYY-MM-DD
        
        if (!grouped[dateKey]) {
            grouped[dateKey] = [];
        }
        grouped[dateKey].push(event);
    });
    
    // Sort events within each date group
    Object.keys(grouped).forEach(date => {
        grouped[date].sort((a, b) => 
            new Date(b.timestamp) - new Date(a.timestamp)
        );
    });
    
    return grouped;
}

// Format date header
function formatDateHeader(dateStr) {
    const date = new Date(dateStr);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    
    if (date.toDateString() === today.toDateString()) {
        return 'Today';
    } else if (date.toDateString() === yesterday.toDateString()) {
        return 'Yesterday';
    } else {
        return date.toLocaleDateString('en-US', { 
            weekday: 'long', 
            year: 'numeric', 
            month: 'long', 
            day: 'numeric' 
        });
    }
}

// Render a single timeline event
function renderTimelineEvent(event) {
    const icon = getTimelineEventIcon(event.type);
    const color = getTimelineEventColor(event.type);
    const time = new Date(event.timestamp);
    const timeAgo = getTimeAgo(time);
    
    let detailsHtml = '';
    if (event.data) {
        detailsHtml = renderEventDetails(event.type, event.data);
    }
    
    return `
        <div class="timeline-event" data-event-type="${event.type}">
            <div class="timeline-event-icon" style="background: ${color};">
                <i class="${icon}"></i>
            </div>
            <div class="timeline-event-content">
                <div class="timeline-event-header">
                    <h4 class="timeline-event-type">${formatEventType(event.type)}</h4>
                    <span class="timeline-event-time">${time.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })}</span>
                </div>
                <p class="timeline-event-description">${event.description || 'No description'}</p>
                ${detailsHtml}
                <div class="timeline-event-footer">
                    <span class="timeline-event-time-ago">${timeAgo}</span>
                </div>
            </div>
        </div>
    `;
}

// Get icon for event type
function getTimelineEventIcon(eventType) {
    const icons = {
        'CASE_CREATED': 'fas fa-plus-circle',
        'CASE_ASSIGNED': 'fas fa-user-plus',
        'CASE_REASSIGNED': 'fas fa-user-friends',
        'CASE_STATUS_CHANGED': 'fas fa-exchange-alt',
        'CASE_PRIORITY_SET': 'fas fa-flag',
        'NOTE': 'fas fa-sticky-note',
        'EVIDENCE_ATTACHED': 'fas fa-paperclip',
        'TRANSACTION': 'fas fa-exchange-alt',
        'SAR_CREATED': 'fas fa-file-alt',
        'SAR_APPROVED': 'fas fa-check-circle',
        'SAR_FILED': 'fas fa-file-export',
        'SAR_REJECTED': 'fas fa-times-circle',
        'ESCALATION': 'fas fa-arrow-up',
        'CASE_ESCALATED': 'fas fa-arrow-up',
        'CASE_DE_ESCALATED': 'fas fa-arrow-down',
        'CASE_RESOLVED': 'fas fa-check-double',
        'CASE_CLOSED': 'fas fa-lock',
        'CASE_REOPENED': 'fas fa-unlock',
        'SLA_DEADLINE': 'fas fa-clock',
        'CASE_LINKED': 'fas fa-link',
        'USER_MENTIONED': 'fas fa-at'
    };
    return icons[eventType] || 'fas fa-circle';
}

// Get color for event type
function getTimelineEventColor(eventType) {
    const colors = {
        'CASE_CREATED': '#27ae60',
        'CASE_ASSIGNED': '#3498db',
        'CASE_REASSIGNED': '#3498db',
        'CASE_STATUS_CHANGED': '#9b59b6',
        'CASE_PRIORITY_SET': '#f39c12',
        'NOTE': '#95a5a6',
        'EVIDENCE_ATTACHED': '#16a085',
        'TRANSACTION': '#e67e22',
        'SAR_CREATED': '#2980b9',
        'SAR_APPROVED': '#27ae60',
        'SAR_FILED': '#16a085',
        'SAR_REJECTED': '#e74c3c',
        'ESCALATION': '#e74c3c',
        'CASE_ESCALATED': '#e74c3c',
        'CASE_DE_ESCALATED': '#95a5a6',
        'CASE_RESOLVED': '#27ae60',
        'CASE_CLOSED': '#34495e',
        'CASE_REOPENED': '#f39c12',
        'SLA_DEADLINE': '#e67e22',
        'CASE_LINKED': '#9b59b6',
        'USER_MENTIONED': '#3498db'
    };
    return colors[eventType] || '#7f8c8d';
}

// Format event type for display
function formatEventType(eventType) {
    return eventType
        .split('_')
        .map(word => word.charAt(0) + word.slice(1).toLowerCase())
        .join(' ');
}

// Render event details based on type
function renderEventDetails(eventType, data) {
    if (!data) return '';
    
    let detailsHtml = '<div class="timeline-event-details">';
    
    switch(eventType) {
        case 'NOTE':
            if (data.content) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Content:</strong>
                    <p>${data.content}</p>
                </div>`;
            }
            if (data.author) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Author:</strong> ${data.author}
                </div>`;
            }
            if (data.internal !== undefined) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Type:</strong> ${data.internal ? 'Internal' : 'Public'}
                </div>`;
            }
            break;
            
        case 'EVIDENCE_ATTACHED':
            if (data.fileName) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>File:</strong> ${data.fileName} (${data.fileType || 'Unknown type'})
                </div>`;
            }
            if (data.description) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Description:</strong> ${data.description}
                </div>`;
            }
            if (data.uploadedBy) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Uploaded by:</strong> ${data.uploadedBy}
                </div>`;
            }
            break;
            
        case 'TRANSACTION':
            if (data.txnId) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Transaction ID:</strong> ${data.txnId}
                </div>`;
            }
            if (data.amountCents) {
                const amount = (data.amountCents / 100);
                const currency =
                    data.currency ||
                    (window.currencyFormatter && typeof window.currencyFormatter.getDefaultCurrency === 'function'
                        ? window.currencyFormatter.getDefaultCurrency()
                        : 'USD');
                const amountDisplay =
                    window.currencyFormatter && typeof window.currencyFormatter.format === 'function'
                        ? window.currencyFormatter.format(amount, currency)
                        : `${currency} ${Number(amount || 0).toFixed(2)}`;
                detailsHtml += `<div class="event-detail-item">
                    <strong>Amount:</strong> ${amountDisplay}
                </div>`;
            }
            if (data.merchantId) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Merchant:</strong> ${data.merchantId}
                </div>`;
            }
            break;
            
        case 'SAR_CREATED':
        case 'SAR_APPROVED':
        case 'SAR_FILED':
            if (data.sarReference) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>SAR Reference:</strong> ${data.sarReference}
                </div>`;
            }
            if (data.status) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Status:</strong> ${data.status}
                </div>`;
            }
            if (data.filingReference) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Filing Reference:</strong> ${data.filingReference}
                </div>`;
            }
            break;
            
        case 'CASE_ASSIGNED':
        case 'CASE_REASSIGNED':
            if (data.assignedTo) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Assigned to:</strong> ${data.assignedTo}
                </div>`;
            }
            break;
            
        case 'CASE_STATUS_CHANGED':
            if (data.status) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>New Status:</strong> ${data.status}
                </div>`;
            }
            break;
            
        case 'CASE_RESOLVED':
            if (data.resolution) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Resolution:</strong> ${data.resolution}
                </div>`;
            }
            if (data.resolutionNotes) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Notes:</strong> ${data.resolutionNotes}
                </div>`;
            }
            break;
            
        case 'ESCALATION':
        case 'CASE_ESCALATED':
            if (data.reason) {
                detailsHtml += `<div class="event-detail-item">
                    <strong>Reason:</strong> ${data.reason}
                </div>`;
            }
            break;
    }
    
    detailsHtml += '</div>';
    return detailsHtml;
}

// Clear timeline view
function clearTimelineView() {
    const container = document.getElementById('timeline-container');
    const caseInfo = document.getElementById('timeline-case-info');
    
    if (container) {
        container.innerHTML = '<div class="timeline-empty-state"><i class="fas fa-clock"></i><p>Select a case to view its timeline</p></div>';
    }
    if (caseInfo) {
        caseInfo.style.display = 'none';
    }
}

// Load timeline (wrapper function)
window.loadCaseTimeline = function() {
    const caseId = document.getElementById('timeline-case-selector')?.value;
    if (caseId) {
        loadCaseTimelineForView(caseId);
    }
};

// Load Case Activities
async function loadCaseActivities(caseId) {
    try {
        const response = await fetch(`cases/${caseId}/activities?page=0&size=20`, {
            credentials: 'include'
        });
        const activities = await response.json();
        
        const activitiesContent = document.getElementById('case-activities-content');
        if (activities.content) {
            activitiesContent.innerHTML = activities.content.map(activity => `
                <div class="activity-item">
                    <div class="activity-icon"><i class="fas fa-${getActivityIcon(activity.activityType)}"></i></div>
                    <div class="activity-content">
                        <strong>${activity.performedBy?.username || 'System'}</strong>
                        <p>${activity.description || activity.activityType}</p>
                        <small>${new Date(activity.performedAt).toLocaleString()}</small>
                    </div>
                </div>
            `).join('');
        }
    } catch (error) {
        console.error('Error loading activities:', error);
    }
}

function getActivityIcon(activityType) {
    const icons = {
        'CASE_CREATED': 'plus-circle',
        'CASE_ASSIGNED': 'user-plus',
        'CASE_ESCALATED': 'arrow-up',
        'NOTE_ADDED': 'sticky-note',
        'EVIDENCE_ADDED': 'paperclip'
    };
    return icons[activityType] || 'circle';
}

// Case Tab Switching
window.showCaseTab = function(tabName) {
    document.querySelectorAll('.case-tab-content').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    
    document.getElementById(`case-${tabName}-tab`).classList.add('active');
    event.target.classList.add('active');
    
    if (tabName === 'timeline' && currentCaseId) {
        loadCaseTimeline(currentCaseId);
    } else if (tabName === 'activities' && currentCaseId) {
        loadCaseActivities(currentCaseId);
    }
};

// Escalate Case
window.escalateCase = async function() {
    if (!currentCaseId) return;
    
    const reason = prompt('Enter escalation reason:');
    if (!reason) return;
    
    try {
        const response = await fetch(`cases/${currentCaseId}/escalate`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ reason })
        });
        
        if (response.ok) {
            alert('Case escalated successfully');
            viewCaseDetail(currentCaseId);
        }
    } catch (error) {
        console.error('Error escalating case:', error);
    }
};

// View Case Network Graph
window.viewCaseNetwork = function() {
    if (!currentCaseId) return;
    if (typeof window.showView === 'function') {
        window.showView('case-network-view');
    }
    loadNetworkGraph(currentCaseId);
};

// Network graph variables
let networkInstance = null;
let networkData = null;
let networkGraphModel = null; // stores last loaded graph (nodes with data)
let networkPhysicsEnabled = true;

// Initialize network graph view
window.initializeNetworkGraphView = function() {
    const caseSelector = document.getElementById('network-case-selector');
    if (caseSelector) {
        caseSelector.addEventListener('change', function() {
            const caseId = this.value;
            if (caseId) {
                loadNetworkGraph(caseId);
            } else {
                clearNetworkGraph();
            }
        });
    }
    
    // Load cases for selector
    loadNetworkCases();
};

// Load cases for network graph selector
window.loadNetworkCases = async function() {
    try {
        const response = await fetch('compliance/cases', {
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const cases = await response.json();
        
        const selector = document.getElementById('network-case-selector');
        if (!selector) return;
        
        selector.innerHTML = '<option value="">Select a case...</option>';
        cases.forEach(caseItem => {
            const option = document.createElement('option');
            option.value = caseItem.id;
            option.textContent = `${caseItem.caseReference} - ${caseItem.description || 'No description'}`;
            selector.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading cases for network graph:', error);
    }
};

// Load Network Graph
window.loadNetworkGraph = async function(caseId) {
    if (!caseId) {
        caseId = document.getElementById('network-case-selector')?.value;
    }
    if (!caseId) {
        alert('Please select a case first');
        return;
    }
    
    const depth = parseInt(document.getElementById('network-depth')?.value || 2);
    const loadingEl = document.getElementById('network-loading');
    const container = document.getElementById('network-canvas');
    const infoPanel = document.getElementById('network-info-panel');
    
    if (loadingEl) loadingEl.style.display = 'block';
    if (container) container.innerHTML = '';
    if (infoPanel) infoPanel.style.display = 'none';
    
    try {
        const response = await fetch(`cases/${caseId}/network?depth=${depth}`, {
            credentials: 'include',
            headers: {
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        });
        
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const graph = await response.json();
        networkGraphModel = graph;
        
        if (loadingEl) loadingEl.style.display = 'none';
        
        // Prepare data for vis-network
        const nodes = graph.nodes.map(node => ({
            id: node.id,
            label: node.label,
            group: node.type.toLowerCase(),
            title: getNodeTooltip(node),
            color: getNodeColor(node.type),
            shape: getNodeShape(node.type),
            size: getNodeSize(node.type)
        }));
        
        const edges = graph.edges.map(edge => ({
            from: edge.from,
            to: edge.to,
            label: edge.label,
            title: edge.type,
            color: getEdgeColor(edge.type),
            arrows: 'to',
            width: 2
        }));
        
        networkData = { nodes: nodes, edges: edges };
        
        // Create network
        const options = {
            nodes: {
                borderWidth: 2,
                shadow: true,
                font: {
                    size: 14,
                    color: '#ffffff'
                }
            },
            edges: {
                font: {
                    size: 12,
                    color: '#ffffff',
                    align: 'middle'
                },
                smooth: {
                    type: 'continuous',
                    roundness: 0.5
                }
            },
            physics: {
                enabled: networkPhysicsEnabled,
                stabilization: {
                    enabled: true,
                    iterations: 200
                },
                barnesHut: {
                    gravitationalConstant: -2000,
                    centralGravity: 0.3,
                    springLength: 95,
                    springConstant: 0.04,
                    damping: 0.09
                }
            },
            interaction: {
                hover: true,
                tooltipDelay: 200,
                zoomView: true,
                dragView: true
            }
        };
        
        if (networkInstance) {
            networkInstance.destroy();
        }
        
        networkInstance = new vis.Network(container, networkData, options);

        // Update physics toggle label
        if (typeof window.updateNetworkPhysicsButton === 'function') {
            window.updateNetworkPhysicsButton();
        }
        
        // Event listeners
        networkInstance.on('click', function(params) {
            if (params.nodes.length > 0) {
                const nodeId = params.nodes[0];
                const node = (networkGraphModel && networkGraphModel.nodes)
                    ? networkGraphModel.nodes.find(n => n.id === nodeId)
                    : graph.nodes.find(n => n.id === nodeId);
                if (node) {
                    showNodeDetails(node);
                }
            } else {
                if (infoPanel) infoPanel.style.display = 'none';
            }
        });
        
        networkInstance.on('hoverNode', function(params) {
            container.style.cursor = 'pointer';
        });
        
        networkInstance.on('blurNode', function(params) {
            container.style.cursor = 'default';
        });
        
    } catch (error) {
        console.error('Error loading network graph:', error);
        if (loadingEl) loadingEl.style.display = 'none';
        if (container) {
            container.innerHTML = '<div class="network-error"><p>Error loading network graph. Please try again.</p></div>';
        }
    }
};

window.updateNetworkPhysicsButton = function() {
    const btn = document.getElementById('network-physics-toggle-btn');
    if (!btn) return;
    btn.innerHTML = `<i class="fas fa-atom"></i> Physics: ${networkPhysicsEnabled ? 'On' : 'Off'}`;
};

window.toggleNetworkPhysics = function() {
    networkPhysicsEnabled = !networkPhysicsEnabled;
    if (networkInstance) {
        networkInstance.setOptions({ physics: { enabled: networkPhysicsEnabled } });
    }
    window.updateNetworkPhysicsButton();
};

window.findNetworkNode = function() {
    const term = document.getElementById('network-search')?.value?.trim().toLowerCase();
    if (!term) {
        alert('Enter a search term (node label).');
        return;
    }
    if (!networkInstance || !networkData || !Array.isArray(networkData.nodes)) {
        alert('Load a network graph first.');
        return;
    }

    const match = networkData.nodes.find(n => (n.label || '').toLowerCase().includes(term));
    if (!match) {
        alert('No matching node found.');
        return;
    }

    networkInstance.selectNodes([match.id]);
    networkInstance.focus(match.id, {
        scale: 1.2,
        animation: { duration: 500, easingFunction: 'easeInOutQuad' }
    });

    // Show details if available
    if (networkGraphModel && Array.isArray(networkGraphModel.nodes)) {
        const node = networkGraphModel.nodes.find(n => n.id === match.id);
        if (node) showNodeDetails(node);
    }
};

window.exportNetworkGraphPng = function() {
    if (!networkInstance) {
        alert('Load a network graph first.');
        return;
    }
    try {
        const canvas = networkInstance.canvas?.frame?.canvas;
        if (!canvas) {
            alert('Unable to export: canvas not available.');
            return;
        }
        const url = canvas.toDataURL('image/png');
        const link = document.createElement('a');
        link.href = url;
        link.download = `case_network_${new Date().toISOString().split('T')[0]}.png`;
        link.click();
    } catch (e) {
        console.error('PNG export failed:', e);
        alert('PNG export failed. See console for details.');
    }
};

// Get node color based on type
function getNodeColor(type) {
    const colors = {
        'CASE': { background: '#1e3a5f', border: '#2d5a8e' },
        'TRANSACTION': { background: '#e67e22', border: '#d35400' },
        'SAR': { background: '#2980b9', border: '#1f618d' },
        'MERCHANT': { background: '#16a085', border: '#138d75' },
        'USER': { background: '#9b59b6', border: '#7d3c98' }
    };
    return colors[type] || { background: '#7f8c8d', border: '#5d6d7e' };
}

// Get node shape based on type
function getNodeShape(type) {
    const shapes = {
        'CASE': 'box',
        'TRANSACTION': 'diamond',
        'SAR': 'triangle',
        'MERCHANT': 'ellipse',
        'USER': 'icon'
    };
    return shapes[type] || 'ellipse';
}

// Get node size based on type
function getNodeSize(type) {
    const sizes = {
        'CASE': 30,
        'TRANSACTION': 20,
        'SAR': 25,
        'MERCHANT': 20,
        'USER': 25
    };
    return sizes[type] || 20;
}

// Get edge color based on type
function getEdgeColor(type) {
    const colors = {
        'RELATED_CASE': '#e74c3c',
        'HAS_TRANSACTION': '#f39c12',
        'HAS_SAR': '#3498db',
        'RELATED_ENTITY': '#16a085',
        'HAS_MERCHANT': '#27ae60',
        'ASSIGNED_TO': '#9b59b6'
    };
    return colors[type] || '#95a5a6';
}

// Get node tooltip
function getNodeTooltip(node) {
    let tooltip = `<strong>${node.label}</strong><br>Type: ${node.type}`;
    
    if (node.data) {
        if (node.type === 'CASE' && node.data.caseReference) {
            tooltip += `<br>Reference: ${node.data.caseReference}`;
            if (node.data.status) tooltip += `<br>Status: ${node.data.status}`;
            if (node.data.priority) tooltip += `<br>Priority: ${node.data.priority}`;
        } else if (node.type === 'TRANSACTION' && node.data.txnId) {
            tooltip += `<br>Transaction ID: ${node.data.txnId}`;
            if (node.data.amountCents) {
                const amount = (node.data.amountCents / 100);
                const currency =
                    node.data.currency ||
                    (window.currencyFormatter && typeof window.currencyFormatter.getDefaultCurrency === 'function'
                        ? window.currencyFormatter.getDefaultCurrency()
                        : 'USD');
                const amountDisplay =
                    window.currencyFormatter && typeof window.currencyFormatter.format === 'function'
                        ? window.currencyFormatter.format(amount, currency)
                        : `${currency} ${Number(amount || 0).toFixed(2)}`;
                tooltip += `<br>Amount: ${amountDisplay}`;
            }
        } else if (node.type === 'SAR' && node.data.sarReference) {
            tooltip += `<br>SAR Reference: ${node.data.sarReference}`;
            if (node.data.status) tooltip += `<br>Status: ${node.data.status}`;
        } else if (node.type === 'USER' && node.data.username) {
            tooltip += `<br>Username: ${node.data.username}`;
            if (node.data.email) tooltip += `<br>Email: ${node.data.email}`;
        }
    }
    
    return tooltip;
}

// Show node details in info panel
function showNodeDetails(node) {
    const infoPanel = document.getElementById('network-info-panel');
    const detailsDiv = document.getElementById('network-node-details');
    
    if (!infoPanel || !detailsDiv) return;
    
    let html = `<div class="node-detail-header">
        <h4>${node.label}</h4>
        <span class="node-type-badge ${node.type.toLowerCase()}">${node.type}</span>
    </div>`;
    
    if (node.data) {
        html += '<div class="node-detail-content">';
        
        if (node.type === 'CASE') {
            html += `<p><strong>Reference:</strong> ${node.data.caseReference || 'N/A'}</p>`;
            html += `<p><strong>Status:</strong> ${node.data.status || 'N/A'}</p>`;
            html += `<p><strong>Priority:</strong> ${node.data.priority || 'N/A'}</p>`;
            if (node.data.description) {
                html += `<p><strong>Description:</strong> ${node.data.description}</p>`;
            }
        } else if (node.type === 'TRANSACTION') {
            html += `<p><strong>Transaction ID:</strong> ${node.data.txnId || 'N/A'}</p>`;
            if (node.data.amountCents) {
                const amount = (node.data.amountCents / 100);
                const currency =
                    node.data.currency ||
                    (window.currencyFormatter && typeof window.currencyFormatter.getDefaultCurrency === 'function'
                        ? window.currencyFormatter.getDefaultCurrency()
                        : 'USD');
                const amountDisplay =
                    window.currencyFormatter && typeof window.currencyFormatter.format === 'function'
                        ? window.currencyFormatter.format(amount, currency)
                        : `${currency} ${Number(amount || 0).toFixed(2)}`;
                html += `<p><strong>Amount:</strong> ${amountDisplay}</p>`;
            }
            if (node.data.merchantId) {
                html += `<p><strong>Merchant:</strong> ${node.data.merchantId}</p>`;
            }
        } else if (node.type === 'SAR') {
            html += `<p><strong>SAR Reference:</strong> ${node.data.sarReference || 'N/A'}</p>`;
            html += `<p><strong>Status:</strong> ${node.data.status || 'N/A'}</p>`;
            if (node.data.jurisdiction) {
                html += `<p><strong>Jurisdiction:</strong> ${node.data.jurisdiction}</p>`;
            }
        } else if (node.type === 'USER') {
            html += `<p><strong>Username:</strong> ${node.data.username || 'N/A'}</p>`;
            if (node.data.email) {
                html += `<p><strong>Email:</strong> ${node.data.email}</p>`;
            }
        } else if (node.type === 'MERCHANT') {
            html += `<p><strong>Merchant ID:</strong> ${node.label.replace('Merchant: ', '')}</p>`;
        }
        
        html += '</div>';
    }
    
    detailsDiv.innerHTML = html;
    infoPanel.style.display = 'block';
}

// Reset network view
window.resetNetworkView = function() {
    if (networkInstance) {
        networkInstance.fit({
            animation: {
                duration: 500,
                easingFunction: 'easeInOutQuad'
            }
        });
    }
};

// Clear network graph
function clearNetworkGraph() {
    const container = document.getElementById('network-canvas');
    const infoPanel = document.getElementById('network-info-panel');
    
    if (networkInstance) {
        networkInstance.destroy();
        networkInstance = null;
    }
    
    if (container) container.innerHTML = '';
    if (infoPanel) infoPanel.style.display = 'none';
    networkData = null;
}

// Filter Cases
window.filterCases = function() {
    const status = document.getElementById('caseStatusFilter')?.value || '';
    // Call the fetchCases function from dashboard.js with the filter
    if (typeof window.fetchCases === 'function') {
        window.fetchCases(status);
    }
};

// Refresh Cases
window.openCaseFilters = function() {
    alert('Case filters functionality - Coming soon!\n\nThis will open a filter panel to search and filter cases by status, priority, assigned user, date range, and other criteria.');
};

window.refreshCases = function() {
    fetchCases();
};

// Helper function for time ago (if not available globally)
if (typeof getTimeAgo === 'undefined') {
    window.getTimeAgo = function(date) {
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`;
        if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
        return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;
    };
}

// ==========================================================================
// CASE QUEUES VIEW - Queue Management UI
// ==========================================================================

let caseQueuesUiInitialized = false;

function initCaseQueuesUiOnce() {
    if (caseQueuesUiInitialized) return;

    const form = document.getElementById('createQueueForm');
    if (form) {
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            await submitCreateQueueForm();
        });
    }

    const modal = document.getElementById('createQueueModal');
    if (modal) {
        window.addEventListener('click', (e) => {
            if (e.target === modal) {
                closeCreateQueueModal();
            }
        });
    }

    caseQueuesUiInitialized = true;
}

function getCaseQueueFetchOptions(method) {
    return {
        method: method || 'GET',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        }
    };
}

window.openCreateQueueModal = function() {
    initCaseQueuesUiOnce();
    const modal = document.getElementById('createQueueModal');
    if (modal) modal.style.display = 'block';
};

window.closeCreateQueueModal = function() {
    const modal = document.getElementById('createQueueModal');
    const form = document.getElementById('createQueueForm');
    if (form) form.reset();
    if (modal) modal.style.display = 'none';
};

async function submitCreateQueueForm() {
    const form = document.getElementById('createQueueForm');
    if (!form) return;

    const formData = new FormData(form);
    const payload = {
        queueName: (formData.get('queueName') || '').toString().trim(),
        targetRole: (formData.get('targetRole') || '').toString().trim(),
        minPriority: (formData.get('minPriority') || '').toString().trim() || null,
        maxQueueSize: (formData.get('maxQueueSize') || '').toString().trim() ? Number(formData.get('maxQueueSize')) : null,
        autoAssign: formData.get('autoAssign') === 'on',
        enabled: formData.get('enabled') === 'on'
    };

    if (!payload.queueName) {
        alert('Queue name is required.');
        return;
    }
    if (!payload.targetRole) {
        alert('Target role is required.');
        return;
    }

    try {
        const res = await fetch('cases/queues', {
            ...getCaseQueueFetchOptions('POST'),
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            let message = `Failed to create queue (HTTP ${res.status})`;
            try {
                const err = await res.json();
                if (err && err.message) message = err.message;
            } catch (_) {
                // ignore
            }
            throw new Error(message);
        }

        closeCreateQueueModal();
        await loadCaseQueuesView();
    } catch (error) {
        console.error('Error creating queue:', error);
        alert(error.message || 'Error creating queue. Please try again.');
    }
}

window.loadCaseQueuesView = async function() {
    initCaseQueuesUiOnce();

    const tbody = document.querySelector('#case-queues-table tbody');
    if (tbody) {
        tbody.innerHTML = '<tr><td colspan="9">Loading queues...</td></tr>';
    }

    try {
        const res = await fetch('cases/queues/overview', getCaseQueueFetchOptions('GET'));
        if (!res.ok) throw new Error(`HTTP ${res.status}`);

        const queues = await res.json();
        const list = Array.isArray(queues) ? queues : [];
        window._lastCaseQueuesOverview = list;

        // Stats
        const total = list.length;
        const enabled = list.filter(q => q.enabled).length;
        const autoAssign = list.filter(q => q.autoAssign).length;
        const queuedCases = list.reduce((acc, q) => acc + (Number(q.queuedNewCount) || 0), 0);

        const totalEl = document.getElementById('queuesTotal');
        const enabledEl = document.getElementById('queuesEnabled');
        const autoEl = document.getElementById('queuesAutoAssign');
        const queuedEl = document.getElementById('queuesQueuedCases');
        if (totalEl) totalEl.textContent = String(total);
        if (enabledEl) enabledEl.textContent = String(enabled);
        if (autoEl) autoEl.textContent = String(autoAssign);
        if (queuedEl) queuedEl.textContent = String(queuedCases);

        if (!tbody) return;
        if (list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="9">No queues found.</td></tr>';
            return;
        }

        tbody.innerHTML = list.map(q => {
            const createdAt = q.createdAt ? new Date(q.createdAt).toLocaleString() : 'N/A';
            const minPriority = q.minPriority || 'None';
            const maxSize = (q.maxQueueSize === null || q.maxQueueSize === undefined) ? '—' : q.maxQueueSize;
            const autoBadge = q.autoAssign ? '<span class="badge badge-info">YES</span>' : '<span class="badge badge-warning">NO</span>';
            const enabledBadge = q.enabled ? '<span class="status-badge resolved">ENABLED</span>' : '<span class="status-badge dismissed">DISABLED</span>';
            const queuedNew = Number(q.queuedNewCount) || 0;

            const toggleLabel = q.enabled ? 'Disable' : 'Enable';
            const toggleIcon = q.enabled ? 'fa-toggle-on' : 'fa-toggle-off';

            return `
                <tr>
                    <td><span class="case-id">${escapeHtml(q.queueName || '')}</span></td>
                    <td>${escapeHtml(q.targetRole || 'N/A')}</td>
                    <td>${escapeHtml(minPriority)}</td>
                    <td>${escapeHtml(String(maxSize))}</td>
                    <td>${autoBadge}</td>
                    <td>${enabledBadge}</td>
                    <td><strong>${queuedNew}</strong></td>
                    <td>${createdAt}</td>
                    <td>
                        <div class="action-btns">
                            <button class="action-btn" title="${toggleLabel}" onclick="toggleQueueEnabled(${q.id}, ${!q.enabled})">
                                <i class="fas ${toggleIcon}"></i>
                            </button>
                            <button class="action-btn" title="Process Auto-Assign" onclick="processQueueNow(${q.id})">
                                <i class="fas fa-play"></i>
                            </button>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (error) {
        console.error('Error loading queues:', error);
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="9">Error loading queues. Please try again.</td></tr>';
        }
    }
};

window.exportCaseQueuesCsv = function() {
    const list = Array.isArray(window._lastCaseQueuesOverview) ? window._lastCaseQueuesOverview : [];
    if (!list.length) {
        alert('No queue data available to export. Refresh the view first.');
        return;
    }

    function csvEscape(v) {
        const s = String(v == null ? '' : v);
        return `"${s.replaceAll('"', '""')}"`;
    }

    const header = [
        'id',
        'queueName',
        'targetRole',
        'minPriority',
        'maxQueueSize',
        'autoAssign',
        'enabled',
        'queuedNewCount',
        'createdAt'
    ];

    let csv = header.join(',') + '\n';
    list.forEach(q => {
        const row = [
            q.id,
            q.queueName,
            q.targetRole,
            q.minPriority || '',
            q.maxQueueSize == null ? '' : q.maxQueueSize,
            q.autoAssign === true ? 'true' : 'false',
            q.enabled === true ? 'true' : 'false',
            q.queuedNewCount == null ? 0 : q.queuedNewCount,
            q.createdAt || ''
        ].map(csvEscape);
        csv += row.join(',') + '\n';
    });

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `case_queues_${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    URL.revokeObjectURL(url);
};

window.toggleQueueEnabled = async function(queueId, enabled) {
    if (!queueId) return;
    try {
        const res = await fetch(`cases/queues/${queueId}`, {
            ...getCaseQueueFetchOptions('PATCH'),
            body: JSON.stringify({ enabled })
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        await loadCaseQueuesView();
    } catch (error) {
        console.error('Error updating queue:', error);
        alert('Failed to update queue. Please try again.');
    }
};

window.processQueueNow = async function(queueId) {
    if (!queueId) return;
    try {
        const res = await fetch(`cases/queues/${queueId}/process`, getCaseQueueFetchOptions('POST'));
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        await loadCaseQueuesView();
    } catch (error) {
        console.error('Error processing queue:', error);
        alert('Failed to process queue. Please try again.');
    }
};

// Minimal HTML escaping for safe table rendering
function escapeHtml(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

