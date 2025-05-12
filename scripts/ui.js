// UI Utilities

// Show message to user
function showMessage(message, isError = false) {
    const statusElement = document.getElementById('statusMessage');
    statusElement.textContent = message;
    statusElement.classList.remove('hidden', 'success', 'error');
    statusElement.classList.add(isError ? 'error' : 'success');

    // Hide after 5 seconds
    setTimeout(() => {
        statusElement.classList.add('hidden');
    }, 5000);
}

// Show section and update navigation
function showSection(sectionId) {
    // Hide all sections
    document.querySelectorAll('.container').forEach(section => {
        section.classList.add('hidden');
    });

    // Show selected section
    document.getElementById(sectionId).classList.remove('hidden');

    // Update navigation active states
    document.querySelectorAll('.navbar a').forEach(link => {
        link.classList.remove('active');
    });

    // Set active nav item
    const navId = 'nav-' + sectionId.replace('-section', '');
    const navItem = document.getElementById(navId);
    if (navItem) {
        navItem.classList.add('active');
    }

    // Load files if showing files section
    if (sectionId === 'files-section' && token) {
        loadFiles();
    }
}

// Display user roles with badges
function displayRoles(roles) {
    const rolesDisplay = document.getElementById('roles-display');
    rolesDisplay.innerHTML = '';

    const adminBadge = document.getElementById('admin-badge');
    adminBadge.classList.add('hidden');

    if (!roles || roles.length === 0) {
        rolesDisplay.textContent = 'None';
        return;
    }

    // Ensure roles is an array
    let rolesArray = roles;
    if (!Array.isArray(roles)) {
        if (typeof roles === 'string') {
            rolesArray = [roles];
        } else if (typeof roles === 'object') {
            rolesArray = Object.values(roles);
        } else {
            rolesArray = [];
        }
    }

    // Log roles for debugging
    console.log("User roles array:", rolesArray);

    rolesArray.forEach(role => {
        if (!role) return; // Skip null/undefined roles

        const badge = document.createElement('span');
        badge.className = `badge ${role.toLowerCase()}`;
        badge.textContent = role;
        rolesDisplay.appendChild(badge);

        // Show admin badge for admin users
        if (role === 'ADMIN') {
            adminBadge.classList.remove('hidden');
            console.log("Admin role detected");
        }
    });
    
    // Add verified badge if user is verified
    if (currentUser && currentUser.verified) {
        const verifiedBadge = document.createElement('span');
        verifiedBadge.className = 'badge verified';
        verifiedBadge.innerHTML = '<i class="fas fa-check-circle"></i> Verified';
        rolesDisplay.appendChild(verifiedBadge);
    }
}

// Handle API responses
async function handleResponse(response) {
    const contentType = response.headers.get("content-type");
    if (contentType && contentType.indexOf("application/json") !== -1) {
        const data = await response.json();
        if (!response.ok) {
            throw new Error(data.error || data.message || 'An error occurred');
        }
        return data;
    } else {
        const text = await response.text();
        if (!response.ok) {
            throw new Error(text || 'An error occurred');
        }
        return text;
    }
}

// Helper function to format file size
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// Helper function to format date
function formatDate(timestamp) {
    return new Date(timestamp).toLocaleString();
}

// Mobile navigation toggle
function setupMobileNavigation() {
    const mobileToggle = document.getElementById('mobile-toggle');
    const mainNav = document.getElementById('main-nav');
    const authNav = document.getElementById('auth-nav');
    
    if (mobileToggle) {
        mobileToggle.addEventListener('click', function() {
            // Toggle current active navigation
            if (!mainNav.classList.contains('hidden')) {
                mainNav.classList.toggle('mobile-expanded');
            } else if (!authNav.classList.contains('hidden')) {
                authNav.classList.toggle('mobile-expanded');
            }
        });
        
        // Close mobile menu after clicking a link
        document.querySelectorAll('.navbar a').forEach(link => {
            link.addEventListener('click', function() {
                mainNav.classList.remove('mobile-expanded');
                authNav.classList.remove('mobile-expanded');
            });
        });
    }
}

// Setup file drop area functionality
document.addEventListener('DOMContentLoaded', function() {
    const fileDropArea = document.querySelector('.file-drop-area');
    const fileInput = document.getElementById('file-input');

    // Setup mobile navigation
    setupMobileNavigation();

    if (fileDropArea && fileInput) {
        // File input change event
        fileInput.addEventListener('change', function() {
            if (this.files && this.files[0]) {
                const fileName = this.files[0].name;
                fileDropArea.innerHTML = `
          <i class="fas fa-file-alt"></i>
          <p>${fileName}</p>
        `;
            }
        });

        // Drag and drop events
        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            fileDropArea.addEventListener(eventName, preventDefaults, false);
        });

        function preventDefaults(e) {
            e.preventDefault();
            e.stopPropagation();
        }

        ['dragenter', 'dragover'].forEach(eventName => {
            fileDropArea.addEventListener(eventName, highlight, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            fileDropArea.addEventListener(eventName, unhighlight, false);
        });

        function highlight() {
            fileDropArea.classList.add('drag-over');
        }

        function unhighlight() {
            fileDropArea.classList.remove('drag-over');
        }

        fileDropArea.addEventListener('drop', handleDrop, false);

        function handleDrop(e) {
            const dt = e.dataTransfer;
            const files = dt.files;
            fileInput.files = files;

            if (files && files[0]) {
                const fileName = files[0].name;
                fileDropArea.innerHTML = `
          <i class="fas fa-file-alt"></i>
          <p>${fileName}</p>
        `;
            }
        }
    }
});

// Create and show modal
function showModal(content) {
    const modal = document.createElement('div');
    modal.className = 'modal';
    modal.innerHTML = `
    <div class="modal-content">
      <span class="close-button">&times;</span>
      ${content}
    </div>
  `;

    document.body.appendChild(modal);

    // Add event to close modal when clicking outside or on close button
    modal.addEventListener('click', function(event) {
        if (event.target === modal || event.target.classList.contains('close-button')) {
            modal.remove();
        }
    });

    return modal;
}