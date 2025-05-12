// File Management Functions

// Function to fetch user information
async function loadUserNames() {
    if (!token) return {};

    try {
        // Only do this for admins
        if (!currentUser || !currentUser.roles || !currentUser.roles.includes('ADMIN')) {
            return {};
        }

        // Get all users (only admins can do this)
        const response = await fetch('http://localhost:8080/api/auth/users', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        const data = await handleResponse(response);

        if (!data.success || !data.data || !data.data.users) {
            return {};
        }

        // Create a mapping of user IDs to usernames
        const userMap = {};
        const users = Object.values(data.data.users);

        users.forEach(user => {
            userMap[user.id] = user.username;
        });

        return userMap;
    } catch (error) {
        console.error("Error loading user data:", error);
        return {};
    }
}

// Handle file upload
async function handleUpload(e) {
    e.preventDefault();
    const button = document.getElementById('upload-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        if (!token) {
            throw new Error('Please login first!');
        }

        const fileInput = document.getElementById('file-input');
        if (!fileInput.files[0]) {
            throw new Error('Please select a file!');
        }

        // Get CSRF token if not already present
        if (!csrfToken) {
            await getCsrfToken();
        }

        const formData = new FormData();
        formData.append('file', fileInput.files[0]);

        const response = await fetch('http://localhost:8080/api/files', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'X-XSRF-TOKEN': csrfToken
            },
            body: formData,
            credentials: 'include'
        });

        const data = await handleResponse(response);

        if (data.success) {
            showMessage('File uploaded successfully!');
            fileInput.value = '';

            // Reset the file drop area
            const fileDropArea = document.querySelector('.file-drop-area');
            if (fileDropArea) {
                fileDropArea.innerHTML = `
          <i class="fas fa-cloud-upload-alt"></i>
          <p>Drag & drop files here or click to browse</p>
        `;
            }

            showSection('files-section');
        } else {
            throw new Error(data.error || 'Upload failed');
        }
    } catch (error) {
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Load files
async function loadFiles() {
    const button = document.getElementById('refresh-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        if (!token) {
            throw new Error('Please login first!');
        }

        // First get user mapping (for admins)
        const userMap = await loadUserNames();
        console.log("User mapping:", userMap);

        // Then get files
        const response = await fetch('http://localhost:8080/api/files', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        console.log("Files response status:", response.status);

        const responseText = await response.text();
        console.log("Raw files response:", responseText);

        try {
            const data = JSON.parse(responseText);
            console.log("Parsed files data:", data);

            if (data.success && Array.isArray(data.data)) {
                // Add username to each file using the userMap
                const filesWithUsernames = data.data.map(file => {
                    if (file.userId && userMap[file.userId]) {
                        return {
                            ...file,
                            username: userMap[file.userId]
                        };
                    }
                    return file;
                });

                console.log("Files with usernames:", filesWithUsernames);
                displayFiles(filesWithUsernames);
            } else {
                throw new Error(data.error || 'Failed to load files');
            }
        } catch (parseError) {
            console.error("Error parsing response:", parseError);
            showMessage("Error loading files: Invalid response from server", true);
        }
    } catch (error) {
        console.error("Error loading files:", error);
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

function viewFile(fileId, fileName) {
    window.open(`http://localhost:8080/api/files/${fileId}`, '_blank');
}

function downloadFile(fileId, fileName) {
    // Create a download URL with the download parameter set to true
    const downloadUrl = `http://localhost:8080/api/files/${fileId}?download=true`;

    // Create a temporary link element
    const a = document.createElement('a');
    a.href = downloadUrl;
    a.download = fileName; // Set suggested filename

    // Add to document, click it, and remove it
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

// Add this function to files.js

// Create a shareable download link
function createShareableLink(fileId, fileName) {
    const fileUrl = `http://localhost:8080/api/files/${fileId}`;
    const downloadUrl = `http://localhost:8080/api/files/${fileId}?download=true`;

    // Create modal content with shareable links
    const modalContent = `
        <h3>Shareable Links for "${fileName}"</h3>
        <div class="share-links">
            <div class="share-link-item">
                <h4><i class="fas fa-eye"></i> View Link</h4>
                <div class="link-container">
                    <input type="text" value="${fileUrl}" readonly class="link-input" id="view-link">
                    <button onclick="copyToClipboard('view-link')" class="btn-secondary">
                        <i class="fas fa-copy"></i> Copy
                    </button>
                </div>
            </div>
            
            <div class="share-link-item">
                <h4><i class="fas fa-download"></i> Download Link</h4>
                <div class="link-container">
                    <input type="text" value="${downloadUrl}" readonly class="link-input" id="download-link">
                    <button onclick="copyToClipboard('download-link')" class="btn-secondary">
                        <i class="fas fa-copy"></i> Copy
                    </button>
                </div>
            </div>
        </div>
    `;

    // Show the modal
    showModal(modalContent);

    // Add styles for the share links
    const styleElement = document.createElement('style');
    styleElement.textContent = `
        .share-links {
            display: flex;
            flex-direction: column;
            gap: 20px;
            margin-top: 20px;
        }
        
        .share-link-item {
            border: 1px solid var(--border-color);
            border-radius: var(--border-radius);
            padding: 15px;
            background-color: #f8f9fa;
        }
        
        .share-link-item h4 {
            margin-top: 0;
            margin-bottom: 10px;
            font-size: 1.1rem;
        }
        
        .link-container {
            display: flex;
            gap: 10px;
        }
        
        .link-input {
            flex: 1;
            padding: 8px 12px;
            border: 1px solid var(--border-color);
            border-radius: var(--border-radius);
            font-size: 0.9rem;
        }
    `;
    document.head.appendChild(styleElement);
}

// Function to copy text to clipboard
function copyToClipboard(elementId) {
    const copyText = document.getElementById(elementId);
    copyText.select();
    copyText.setSelectionRange(0, 99999); // For mobile devices

    navigator.clipboard.writeText(copyText.value)
        .then(() => {
            // Visual feedback on successful copy
            const originalText = copyText.nextElementSibling.innerHTML;
            copyText.nextElementSibling.innerHTML = '<i class="fas fa-check"></i> Copied!';

            setTimeout(() => {
                copyText.nextElementSibling.innerHTML = originalText;
            }, 2000);
        })
        .catch(err => {
            console.error('Failed to copy: ', err);
            alert('Failed to copy the link. Please try again.');
        });
}

// Display files
function displayFiles(files) {
    console.log("displayFiles received:", files);

    const filesList = document.getElementById('files-list');

    // Check if user is admin
    const isAdmin = currentUser && Array.isArray(currentUser.roles) &&
        currentUser.roles.some(role => role === 'ADMIN');

    // If no files or empty array
    if (!files || (Array.isArray(files) && files.length === 0)) {
        console.log("No files found or empty array");
        filesList.innerHTML = '<p class="empty-message">No files found.</p>';
        return;
    }

    // Ensure files is an array
    let filesArray = files;
    if (!Array.isArray(files)) {
        if (typeof files === 'object') {
            console.log("Converting object to array:", files);
            filesArray = Object.values(files);
            console.log("After conversion:", filesArray);
        } else if (typeof files === 'string') {
            try {
                // Try to parse if it's a JSON string
                const parsed = JSON.parse(files);
                filesArray = Array.isArray(parsed) ? parsed : Object.values(parsed);
                console.log("Parsed from string:", filesArray);
            } catch (e) {
                console.log("Not a valid JSON string:", files);
                filesList.innerHTML = '<p class="empty-message">Error parsing file data.</p>';
                return;
            }
        } else {
            console.log("Not a recognized format:", files);
            filesList.innerHTML = '<p class="empty-message">No files found.</p>';
            return;
        }
    }

    // Check if the array is empty
    if (filesArray.length === 0) {
        console.log("Empty array of files");
        filesList.innerHTML = '<p class="empty-message">No files found.</p>';
        return;
    }

    console.log("Processing files array with length:", filesArray.length);

    let html = '';
    filesArray.forEach(file => {
        console.log("Processing file:", file);
        // Ensure file has all required properties
        if (!file || !file.id || !file.fileName) {
            console.log("Skipping invalid file object:", file);
            return;
        }

        const isImage = file.contentType && file.contentType.startsWith('image/');
        const fileIcon = isImage ?
            `<img src="http://localhost:8080/api/files/${file.id}" alt="${file.fileName}">` :
            `<i class="fas fa-file fa-4x" style="color: var(--primary-color); margin: 20px 0;"></i>`;

        // Create HTML for each file card
        html += `
      <div class="file-card">
        <div class="file-preview">
          ${fileIcon}
        </div>
        <div class="file-details">
          <h3>${file.fileName}</h3>`;

        // If admin, show the username if available
        if (isAdmin && file.userId) {
            const displayName = file.username || `User ID: ${file.userId}`;
            html += `<p>Uploaded by: <span class="file-owner">${displayName}</span></p>`;
        }

        // Show public/moderation status if applicable
        if (file.isPublic) {
            let statusBadge = '';
            if (file.moderationStatus === 'PENDING') {
                statusBadge = '<span class="badge pending">Pending Moderation</span>';
            } else if (file.moderationStatus === 'APPROVED') {
                statusBadge = '<span class="badge success">Public</span>';
            } else if (file.moderationStatus === 'REJECTED') {
                statusBadge = '<span class="badge error">Rejected</span>';
            } else {
                statusBadge = '<span class="badge success">Public</span>';
            }
            html += `<p>Status: ${statusBadge}</p>`;
        }

        html += `
          <p>ID: ${file.id}</p>
          <p>Type: ${file.contentType || 'Unknown'}</p>
          <p>Size: ${formatFileSize(file.size || 0)}</p>
          <p>Uploaded: ${formatDate(file.uploadDate || Date.now())}</p>
        </div>
        <div class="file-actions">
          <button onclick="viewFile('${file.id}', '${file.fileName}')" class="btn-primary">
            <i class="fas fa-eye"></i> View
          </button>
          <button onclick="downloadFile('${file.id}', '${file.fileName}')" class="btn-primary">
            <i class="fas fa-download"></i> Download
          </button>
          <button onclick="createShareableLink('${file.id}', '${file.fileName}')" class="btn-secondary">
            <i class="fas fa-share-alt"></i> Share
          </button>
          ${isImage ? `
            <button onclick="window.open('http://localhost:8080/api/files/${file.id}/thumbnail', '_blank')" class="btn-secondary">
              <i class="fas fa-image"></i> Thumbnail
            </button>
          ` : ''}
          ${isAdmin || (currentUser && file.userId === currentUser.id) ? `
            <button onclick="toggleFileVisibility('${file.id}', ${!file.isPublic})" class="btn-secondary">
              <i class="fas fa-${file.isPublic ? 'lock' : 'globe'}"></i> ${file.isPublic ? 'Make Private' : 'Request Public Access'}
            </button>
          ` : ''}
          <button onclick="deleteFile('${file.id}')" class="btn-danger">
            <i class="fas fa-trash"></i> Delete
          </button>
        </div>
      </div>`;
    });

    if (html === '') {
        filesList.innerHTML = '<p class="empty-message">No valid files found to display.</p>';
    } else {
        filesList.innerHTML = html;
    }
    console.log("Files display completed");
}

// Delete file
async function deleteFile(fileId) {
    if (!token) {
        showMessage('Please login first!', true);
        showSection('login-section');
        return;
    }

    if (!confirm('Are you sure you want to delete this file?')) {
        return;
    }

    try {
        // Get CSRF token if not already present
        if (!csrfToken) {
            await getCsrfToken();
        }

        const response = await fetch(`http://localhost:8080/api/files/${fileId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': `Bearer ${token}`,
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include'
        });

        const data = await handleResponse(response);

        if (data.success) {
            showMessage('File deleted successfully!');
            loadFiles();
        } else {
            throw new Error(data.error || 'Deletion failed');
        }
    } catch (error) {
        showMessage(error.message, true);
    }
}

// View files uploaded by a specific user
async function viewUserFiles(userId, username) {
    try {
        showMessage(`Loading files for user: ${username}...`);

        const response = await fetch(`http://localhost:8080/api/files/stats/${userId}`, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        const data = await handleResponse(response);

        if (data.success) {
            const stats = data.data;

            // Ensure files is an array
            let files = stats.files || [];
            if (!Array.isArray(files) && typeof files === 'object') {
                files = Object.values(files);
            }

            // Create modal content to display user files
            const displayName = username || `User ${userId.substr(0, 8)}`;

            let modalContent = `
        <h2><i class="fas fa-user"></i> Files uploaded by ${displayName}</h2>
        <div class="user-stats">
          <div>
            <i class="fas fa-file"></i>
            <p>Total Files: <strong>${stats.totalFiles}</strong></p>
          </div>
          <div>
            <i class="fas fa-hdd"></i>
            <p>Total Size: <strong>${formatFileSize(stats.totalSize)}</strong></p>
          </div>
          <div>
            <i class="fas fa-calendar-alt"></i>
            <p>Last Upload: <strong>${stats.lastUpload ? formatDate(stats.lastUpload) : 'Never'}</strong></p>
          </div>
        </div>
      `;

            if (files.length === 0) {
                modalContent += '<p class="empty-message">This user has not uploaded any files yet.</p>';
            } else {
                modalContent += '<div class="files-grid">';
                files.forEach(file => {
                    const isImage = file.contentType && file.contentType.startsWith('image/');
                    const fileIcon = isImage ?
                        `<img src="http://localhost:8080/api/files/${file.id}/thumbnail/100" alt="${file.fileName}">` :
                        `<i class="fas fa-file fa-3x" style="color: var(--primary-color); margin: 10px 0;"></i>`;

                    modalContent += `
            <div class="file-item">
              <div class="file-preview">
                ${fileIcon}
              </div>
              <div class="file-details">
                <p><strong>${file.fileName}</strong></p>
                <p>Type: ${file.contentType}</p>
                <p>Size: ${formatFileSize(file.size)}</p>
                <p>Uploaded: ${formatDate(file.uploadDate)}</p>
              </div>
              <div class="file-actions">
                <button onclick="window.open('http://localhost:8080/api/files/${file.id}', '_blank')" class="btn-primary">
                  <i class="fas fa-eye"></i> View
                </button>
              </div>
            </div>
          `;
                });
                modalContent += '</div>';
            }

            // Show the modal with the content
            showModal(modalContent);
        } else {
            throw new Error(data.error || 'Failed to load user files');
        }
    } catch (error) {
        console.error("Error viewing user files:", error);
        showMessage(error.message, true);
    }
}

// Load public files
async function loadPublicFiles() {
    const button = document.getElementById('load-public-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        // Fetch public files
        const response = await fetch('http://localhost:8080/api/files/public', {
            method: 'GET'
        });

        const data = await handleResponse(response);

        if (data.success && Array.isArray(data.data)) {
            displayPublicFiles(data.data);
        } else {
            throw new Error(data.error || 'Failed to load public files');
        }
    } catch (error) {
        console.error("Error loading public files:", error);
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Display public files
function displayPublicFiles(files) {
    const filesList = document.getElementById('public-files-list');

    if (!files || files.length === 0) {
        filesList.innerHTML = '<p class="empty-message">No public files found.</p>';
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
          <p>Type: ${file.contentType || 'Unknown'}</p>
          <p>Size: ${formatFileSize(file.size || 0)}</p>
          <p>Uploaded: ${formatDate(file.uploadDate || Date.now())}</p>
        </div>
        <div class="file-actions">
          <button onclick="viewFile('${file.id}', '${file.fileName}')" class="btn-primary">
            <i class="fas fa-eye"></i> View
          </button>
          <button onclick="downloadFile('${file.id}', '${file.fileName}')" class="btn-primary">
            <i class="fas fa-download"></i> Download
          </button>
        </div>
      </div>`;
    });

    filesList.innerHTML = html;
}

// Toggle file visibility
async function toggleFileVisibility(fileId, makePublic) {
    if (!token) {
        showMessage('Please login first!', true);
        return;
    }

    try {
        // Get CSRF token if not already present
        if (!csrfToken) {
            await getCsrfToken();
        }

        const response = await fetch(`http://localhost:8080/api/files/${fileId}/visibility`, {
            method: 'PATCH',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken
            },
            body: JSON.stringify({ isPublic: makePublic }),
            credentials: 'include'
        });

        const data = await handleResponse(response);

        if (data.success) {
            if (makePublic) {
                showMessage('Public access request submitted. Awaiting admin approval.');
            } else {
                showMessage('File is now private.');
            }
            loadFiles(); // Refresh the files list
        } else {
            throw new Error(data.error || 'Failed to update file visibility');
        }
    } catch (error) {
        showMessage(error.message, true);
    }
}