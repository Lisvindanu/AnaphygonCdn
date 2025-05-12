// Global variables
let token = localStorage.getItem('cdnToken');
let csrfToken = '';
let currentUser = JSON.parse(localStorage.getItem('cdnUser')) || null;

// Initialize the application state
async function initializeApp() {
    const mainNav = document.getElementById('main-nav');
    const authNav = document.getElementById('auth-nav');
    const mobileToggle = document.getElementById('mobile-toggle');
    
    // Reset mobile expanded state
    mainNav.classList.remove('mobile-expanded');
    authNav.classList.remove('mobile-expanded');
    
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

        // Public files section is available to all logged-in users
        document.getElementById('nav-public').classList.remove('hidden');

        // Immediately check verification status from server and update UI
        try {
            const isVerified = await checkVerificationStatus();
            
            // Update user object with current verification status
            if (currentUser.verified !== isVerified) {
                currentUser.verified = isVerified;
                localStorage.setItem('cdnUser', JSON.stringify(currentUser));
                console.log("Updated user verification status to:", isVerified);
                
                // Refresh the roles display to show/hide verified badge
                displayRoles(currentUser.roles);
            }
            
            // Only show banner if actually not verified according to server
            if (!isVerified) {
                showVerificationStatusBanner();
            } else {
                // Remove any existing banner if user is verified
                const existingBanner = document.querySelector('.verification-banner');
                if (existingBanner) {
                    existingBanner.remove();
                }
            }
        } catch (error) {
            console.error("Error checking verification status:", error);
            // Fall back to stored value if server check fails
            if (!currentUser.verified) {
                showVerificationStatusBanner();
            }
        }

        showSection('files-section');
    } else {
        document.getElementById('auth-nav').classList.remove('hidden');
        document.getElementById('main-nav').classList.add('hidden');
        document.getElementById('user-info').classList.add('hidden');
        
        // Hide the public files nav when logged out
        document.getElementById('nav-public').classList.add('hidden');
        
        showSection('login-section');
    }
}

// Initialize the application
document.addEventListener('DOMContentLoaded', function() {
    // Check for email verification token in URL
    const urlParams = new URLSearchParams(window.location.search);
    const verificationToken = urlParams.get('token');
    
    if (verificationToken) {
        // If there's a verification token, handle it
        verifyEmailFromUrl();
    } else {
        // Regular initialization - now async
        initializeApp().catch(error => {
            console.error("Error during app initialization:", error);
        });
    }
    
    getCsrfToken();
});