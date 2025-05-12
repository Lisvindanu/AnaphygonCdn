// Global variables
let token = localStorage.getItem('cdnToken');
let csrfToken = '';
let currentUser = JSON.parse(localStorage.getItem('cdnUser')) || null;

// Initialize the application state
function initializeApp() {
    if (token && currentUser) {
        document.getElementById('auth-nav').classList.add('hidden');
        document.getElementById('main-nav').classList.remove('hidden');
        document.getElementById('user-info').classList.remove('hidden');
        document.getElementById('username-display').textContent = currentUser.username;

        // Ensure roles is an array
        if (!currentUser.roles) {
            currentUser.roles = [];
        } else if (typeof currentUser.roles === 'string') {
            currentUser.roles = [currentUser.roles];
        } else if (typeof currentUser.roles === 'object' && !Array.isArray(currentUser.roles)) {
            // Handle case when roles is an object (from JSON)
            currentUser.roles = Object.values(currentUser.roles);
        }

        console.log("Current user:", currentUser);
        displayRoles(currentUser.roles);

        // Check for admin privileges - safer check for ADMIN role
        const hasAdminRole = Array.isArray(currentUser.roles) &&
            currentUser.roles.some(role => role === 'ADMIN');

        // Show admin sections if user has admin role
        const adminNav = document.getElementById('nav-admin');
        const usersNav = document.getElementById('nav-users');

        if (hasAdminRole) {
            adminNav.classList.remove('hidden');
            usersNav.classList.remove('hidden');

            // Also update the admin section content when they're an admin
            setupAdminFileManagement();

            console.log("Admin navigation enabled");
        } else {
            adminNav.classList.add('hidden');
            usersNav.classList.add('hidden');
        }

        showSection('files-section');
    } else {
        document.getElementById('auth-nav').classList.remove('hidden');
        document.getElementById('main-nav').classList.add('hidden');
        document.getElementById('user-info').classList.add('hidden');
        showSection('login-section');
    }
}

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    initializeApp();
    getCsrfToken();
});