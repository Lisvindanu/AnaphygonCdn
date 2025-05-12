// Admin Functions

// Load system info for admin panel
async function loadSystemInfo() {
    const button = document.getElementById('system-info-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        const systemInfoDiv = document.getElementById('system-info');
        systemInfoDiv.innerHTML = '<p>Loading system information...</p>';

        const response = await fetch('http://localhost:8080/api/metrics/storage', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        const data = await handleResponse(response);

        if (data) {
            const totalSizeMB = (data.totalSize / (1024 * 1024)).toFixed(2);
            const diskTotalGB = (data.diskTotalSpace / (1024 * 1024 * 1024)).toFixed(2);
            const diskUsedGB = (data.diskUsedSpace / (1024 * 1024 * 1024)).toFixed(2);
            const diskFreeGB = (data.diskUsableSpace / (1024 * 1024 * 1024)).toFixed(2);

            // Calculate disk usage percentage
            const usagePercent = ((data.diskUsedSpace / data.diskTotalSpace) * 100).toFixed(1);

            systemInfoDiv.innerHTML = `
        <div class="info-card">
          <h4><i class="fas fa-server"></i> Storage Information</h4>
          <div class="storage-stats">
            <div class="stat-item">
              <i class="fas fa-file"></i>
              <span>Total Files:</span>
              <strong>${data.totalFiles}</strong>
            </div>
            <div class="stat-item">
              <i class="fas fa-hdd"></i>
              <span>Total Size:</span>
              <strong>${totalSizeMB} MB</strong>
            </div>
            <div class="stat-item">
              <i class="fas fa-server"></i>
              <span>Disk Space:</span>
              <strong>${diskUsedGB} GB / ${diskTotalGB} GB</strong>
            </div>
            <div class="stat-item">
              <i class="fas fa-database"></i>
              <span>Free Space:</span>
              <strong>${diskFreeGB} GB</strong>
            </div>
          </div>
          
          <div class="usage-bar-container">
            <div class="usage-label">Disk Usage: ${usagePercent}%</div>
            <div class="usage-bar">
              <div class="usage-fill" style="width: ${usagePercent}%"></div>
            </div>
          </div>
          
          <h4><i class="fas fa-chart-pie"></i> Files by Type</h4>
          <div class="file-types">
            ${Object.entries(data.filesByType || {})
                .sort((a, b) => b[1] - a[1]) // Sort by count (highest first)
                .map(([type, count]) => `
                <div class="file-type-item">
                  <i class="${getFileTypeIcon(type)}"></i>
                  <span>${type || 'Unknown'}</span>
                  <strong>${count}</strong>
                </div>
              `).join('')}
          </div>
        </div>
      `;

            // Add CSS for the stats display
            const styleElement = document.createElement('style');
            styleElement.textContent = `
        .storage-stats {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
          gap: 15px;
          margin: 15px 0;
        }
        
        .stat-item {
          padding: 10px;
          background-color: #f8f9fa;
          border-radius: 8px;
          border: 1px solid #e9ecef;
          display: flex;
          flex-direction: column;
          align-items: center;
          text-align: center;
        }
        
        .stat-item i {
          font-size: 1.5rem;
          color: var(--primary-color);
          margin-bottom: 8px;
        }
        
        .stat-item strong {
          font-size: 1.1rem;
          color: var(--dark-bg);
          margin-top: 5px;
        }
        
        .usage-bar-container {
          margin: 15px 0;
        }
        
        .usage-label {
          margin-bottom: 5px;
          font-weight: 500;
        }
        
        .usage-bar {
          height: 10px;
          background-color: #e9ecef;
          border-radius: 5px;
          overflow: hidden;
        }
        
        .usage-fill {
          height: 100%;
          background-color: ${usagePercent > 90 ? 'var(--error-color)' :
                usagePercent > 70 ? 'var(--warning-color)' :
                    'var(--success-color)'};
          border-radius: 5px;
          transition: width 0.5s ease;
        }
        
        .file-types {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
          gap: 10px;
          margin-top: 15px;
        }
        
        .file-type-item {
          padding: 10px;
          background-color: #f8f9fa;
          border-radius: 8px;
          border: 1px solid #e9ecef;
          display: flex;
          flex-direction: column;
          align-items: center;
          text-align: center;
        }
        
        .file-type-item i {
          font-size: 1.2rem;
          color: var(--primary-color);
          margin-bottom: 5px;
        }
        
        .file-type-item strong {
          font-size: 1.1rem;
          margin-top: 5px;
        }
      `;
            document.head.appendChild(styleElement);

        } else {
            systemInfoDiv.innerHTML = '<p class="empty-message">Error loading system information.</p>';
        }
    } catch (error) {
        showMessage(error.message, true);
        document.getElementById('system-info').innerHTML =
            '<p class="empty-message">Error loading system information.</p>';
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Load all users (admin only)
async function loadUsers() {
    const button = document.getElementById('load-users-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        if (!token) {
            throw new Error('Please login first!');
        }

        const response = await fetch('http://localhost:8080/api/auth/users', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        // Log the raw response for debugging
        console.log("Raw response:", await response.clone().text());

        const data = await handleResponse(response);
        console.log("Parsed response data:", data);

        if (data.success) {
            // Handle different response structures
            let usersData = data.data;

            // If data.data is an object with a 'users' property, use that
            if (usersData && typeof usersData === 'object' && usersData.users) {
                usersData = usersData.users;
            }

            displayUsers(usersData);
        } else {
            throw new Error(data.error || 'Failed to load users');
        }
    } catch (error) {
        console.error("Error loading users:", error);
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Display users list
function displayUsers(usersData) {
    console.log("Raw users data:", usersData);
    const usersList = document.getElementById('users-list');

    // Convert users to array if it's not already
    let users = [];

    if (!usersData) {
        usersList.innerHTML = '<p class="empty-message">No users found.</p>';
        return;
    }

    // Handle different formats of user data
    if (Array.isArray(usersData)) {
        users = usersData;
    } else if (typeof usersData === 'object') {
        // If we received an object with a 'users' property
        if (usersData.users) {
            if (Array.isArray(usersData.users)) {
                users = usersData.users;
            } else if (typeof usersData.users === 'object') {
                // If users is an object with numeric keys, convert to array
                users = Object.values(usersData.users);
            }
        } else {
            // If just a simple object with numeric keys, convert to array
            users = Object.values(usersData);
        }
    }

    console.log("Processed users array:", users);

    if (users.length === 0) {
        usersList.innerHTML = '<p class="empty-message">No users found.</p>';
        return;
    }

    let html = '<div class="users-grid">';
    users.forEach(user => {
        if (!user) return; // Skip null/undefined users

        const lastLoginText = user.lastLogin ? formatDate(user.lastLogin) : 'Never';
        const userCreated = formatDate(user.createdAt || Date.now());

        // Ensure roles is an array
        let roles = [];
        if (user.roles) {
            if (Array.isArray(user.roles)) {
                roles = user.roles;
            } else if (typeof user.roles === 'string') {
                roles = [user.roles];
            } else if (typeof user.roles === 'object') {
                roles = Object.values(user.roles);
            }
        }

        // Generate role badges
        const roleBadges = roles.map(role => {
            if (!role) return '';
            return `<span class="badge ${role.toLowerCase()}">${role}</span>`;
        }).join(' ');

        html += `
      <div class="user-card">
        <div class="user-header">
          <h3><i class="fas fa-user"></i> ${user.username}</h3>
          <div class="user-roles">
            ${roleBadges}
          </div>
        </div>
        <div class="user-details">
          <p><i class="fas fa-envelope"></i> <strong>Email:</strong> ${user.email}</p>
          <p><i class="fas fa-fingerprint"></i> <strong>User ID:</strong> ${user.id}</p>
          <p><i class="fas fa-shield-alt"></i> <strong>Status:</strong> 
            ${user.active ?
            '<span class="active-badge"><i class="fas fa-check-circle"></i> Active</span>' :
            '<span class="inactive-badge"><i class="fas fa-times-circle"></i> Inactive</span>'}
          </p>
          <p><i class="fas fa-calendar-plus"></i> <strong>Created:</strong> ${userCreated}</p>
          <p><i class="fas fa-sign-in-alt"></i> <strong>Last Login:</strong> ${lastLoginText}</p>
        </div>
        <div class="user-actions">
          <button onclick="viewUserFiles('${user.id}', '${user.username}')" class="btn-primary">
            <i class="fas fa-folder-open"></i> View Uploads
          </button>
        </div>
      </div>
    `;
    });
    html += '</div>';
    usersList.innerHTML = html;
}

// Get appropriate icon for file type
function getFileTypeIcon(mimeType) {
    if (!mimeType) return 'fas fa-file';

    if (mimeType.startsWith('image/')) return 'fas fa-file-image';
    if (mimeType.startsWith('video/')) return 'fas fa-file-video';
    if (mimeType.startsWith('audio/')) return 'fas fa-file-audio';
    if (mimeType.startsWith('text/')) return 'fas fa-file-alt';
    if (mimeType.includes('pdf')) return 'fas fa-file-pdf';
    if (mimeType.includes('word') || mimeType.includes('document')) return 'fas fa-file-word';
    if (mimeType.includes('excel') || mimeType.includes('spreadsheet')) return 'fas fa-file-excel';
    if (mimeType.includes('powerpoint') || mimeType.includes('presentation')) return 'fas fa-file-powerpoint';
    if (mimeType.includes('zip') || mimeType.includes('compressed')) return 'fas fa-file-archive';
    if (mimeType.includes('javascript') || mimeType.includes('json')) return 'fas fa-file-code';

    return 'fas fa-file';
}

// Add function to load all files for admins
function loadAllFiles() {
    const button = document.getElementById('admin-view-files-button');
    button.disabled = true;
    button.textContent = 'Loading...';

    try {
        loadFiles(); // Use the existing loadFiles function

        // Show the files section to display the results
        showSection('files-section');
    } catch (error) {
        console.error("Error loading all files:", error);
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.textContent = 'View All Files';
    }
}

// Setup admin file management UI
function setupAdminFileManagement() {
    // Get the admin section container
    const adminSection = document.getElementById('admin-section');

    // Check if the file management card already exists
    if (!document.getElementById('admin-file-management-card')) {
        // Create the file management card
        const fileManagementCard = document.createElement('div');
        fileManagementCard.id = 'admin-file-management-card';
        fileManagementCard.className = 'admin-card';
        fileManagementCard.innerHTML = `
      <h3><i class="fas fa-files-medical"></i> File Management</h3>
      <p>As an administrator, you can view and manage all files in the system.</p>
      <button onclick="loadAllFiles()" id="admin-view-files-button" class="btn-primary">
        <i class="fas fa-folder-open"></i> View All Files
      </button>
    `;

        // Add it to the admin section
        adminSection.appendChild(fileManagementCard);
    }
}

// Load pending files for moderation
async function loadPendingFiles() {
    const button = document.getElementById('load-pending-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        if (!token) {
            throw new Error('Please login first!');
        }

        // Custom endpoint for admin to see pending files
        const response = await fetch('http://localhost:8080/api/files?moderationStatus=PENDING&isPublic=true', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        const data = await handleResponse(response);

        if (data.success && Array.isArray(data.data)) {
            displayPendingFiles(data.data);
        } else {
            throw new Error(data.error || 'Failed to load pending files');
        }
    } catch (error) {
        console.error("Error loading pending files:", error);
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Display pending files
function displayPendingFiles(files) {
    const filesList = document.getElementById('pending-files-list');

    if (!files || files.length === 0) {
        filesList.innerHTML = '<p class="empty-message">No files pending moderation.</p>';
        return;
    }

    let html = '';
    files.forEach(file => {
        const isImage = file.contentType && file.contentType.startsWith('image/');
        const fileIcon = isImage ?
            `<img src="http://localhost:8080/api/files/${file.id}/thumbnail" alt="${file.fileName}">` :
            `<i class="fas fa-file fa-4x" style="color: var(--primary-color); margin: 20px 0;"></i>`;

        html += `
      <div class="file-card">
        <div class="file-preview">
          ${fileIcon}
        </div>
        <div class="file-details">
          <h3>${file.fileName}</h3>
          <p>User ID: ${file.userId || 'Unknown'}</p>
          <p>Type: ${file.contentType || 'Unknown'}</p>
          <p>Size: ${formatFileSize(file.size || 0)}</p>
          <p>Uploaded: ${formatDate(file.uploadDate || Date.now())}</p>
          <p>Status: <span class="badge pending">Pending</span></p>
        </div>
        <div class="file-actions">
          <button onclick="viewFile('${file.id}', '${file.fileName}')" class="btn-primary">
            <i class="fas fa-eye"></i> View
          </button>
          <button onclick="moderateFile('${file.id}', 'APPROVED')" class="btn-success">
            <i class="fas fa-check"></i> Approve
          </button>
          <button onclick="moderateFile('${file.id}', 'REJECTED')" class="btn-danger">
            <i class="fas fa-times"></i> Reject
          </button>
        </div>
      </div>`;
    });

    filesList.innerHTML = html;
}

// Moderate a file
async function moderateFile(fileId, status) {
    if (!token) {
        showMessage('Please login first!', true);
        return;
    }

    try {
        // Get CSRF token if not already present
        if (!csrfToken) {
            await getCsrfToken();
        }

        const response = await fetch(`http://localhost:8080/api/files/${fileId}/moderate`, {
            method: 'PATCH',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken
            },
            body: JSON.stringify({ status: status }),
            credentials: 'include'
        });

        const data = await handleResponse(response);

        if (data.success) {
            showMessage(`File has been ${status.toLowerCase()}`);
            loadPendingFiles(); // Refresh the pending files list
        } else {
            throw new Error(data.error || 'Failed to moderate file');
        }
    } catch (error) {
        showMessage(error.message, true);
    }
}