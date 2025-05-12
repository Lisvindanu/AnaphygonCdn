// Authentication Functions

// Fetch CSRF token
async function getCsrfToken() {
    try {
        const response = await fetch('http://localhost:8080/api/auth/csrf', {
            method: 'GET',
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to fetch CSRF token');
        }

        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'XSRF-TOKEN') {
                csrfToken = value;
                return value;
            }
        }
        return null;
    } catch (error) {
        console.error('Error fetching CSRF token:', error);
        showMessage('Failed to fetch CSRF token: ' + error.message, true);
        return null;
    }
}

// Handle login
async function handleLogin(e) {
    e.preventDefault();
    const button = document.getElementById('login-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        const usernameOrEmail = document.getElementById('login-username').value;
        const password = document.getElementById('login-password').value;

        const response = await fetch('http://localhost:8080/api/auth/login', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ usernameOrEmail, password }),
            credentials: 'include'
        });

        const data = await handleResponse(response);

        // Log the full response for debugging
        console.log("Full login response:", data);

        if (data.success) {
            token = data.data.token;

            // Extract roles properly from JSON response
            let roles = [];
            if (data.data.roles) {
                if (Array.isArray(data.data.roles)) {
                    roles = data.data.roles;
                } else if (typeof data.data.roles === 'string') {
                    roles = [data.data.roles];
                } else if (typeof data.data.roles === 'object') {
                    // Extract values from object if roles is a JSON object with indices as keys
                    roles = Object.values(data.data.roles);
                }
            }

            console.log("Parsed roles:", roles);

            currentUser = {
                id: data.data.userId,
                username: data.data.username,
                roles: roles,
                verified: data.data.verified || false
            };

            localStorage.setItem('cdnToken', token);
            localStorage.setItem('cdnUser', JSON.stringify(currentUser));

            showMessage('Logged in successfully!');
            initializeApp();
        } else {
            throw new Error(data.error || 'Login failed');
        }
    } catch (error) {
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Handle register
async function handleRegister(e) {
    e.preventDefault();
    const button = document.getElementById('register-button');
    button.disabled = true;
    button.classList.add('loading');

    try {
        const username = document.getElementById('register-username').value;
        const email = document.getElementById('register-email').value;
        const password = document.getElementById('register-password').value;

        console.log("Attempting to register with:", { username, email, password: "***" });

        // Try to get CSRF token
        try {
            await getCsrfToken();
            console.log("CSRF token obtained:", csrfToken);
        } catch (e) {
            console.warn("Failed to get CSRF token, continuing without it:", e);
        }

        const headers = {
            'Content-Type': 'application/json'
        };

        // Add CSRF token if available
        if (csrfToken) {
            headers['X-XSRF-TOKEN'] = csrfToken;
        }

        console.log("Sending registration request with headers:", headers);

        const response = await fetch('http://localhost:8080/api/auth/register', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ username, email, password }),
            credentials: 'include'
        });

        console.log("Registration response status:", response.status);

        // Handle different HTTP status codes
        if (response.status === 401) {
            throw new Error("Authentication error. CSRF token might be invalid or missing.");
        }

        // Get the raw response text first for debugging
        const responseText = await response.text();
        console.log("Raw response text:", responseText);

        // Try to parse as JSON
        let data;
        try {
            data = JSON.parse(responseText);
            console.log("Parsed JSON response:", data);
        } catch (e) {
            console.error("Failed to parse response as JSON:", e);
            throw new Error(`Server returned invalid JSON: ${responseText}`);
        }

        // Check for success in the parsed data
        if (data.success) {
            console.log("Registration successful, processing response data");
            token = data.data.token;

            // Extract roles properly from JSON response
            let roles = [];
            if (data.data.roles) {
                if (Array.isArray(data.data.roles)) {
                    roles = data.data.roles;
                } else if (typeof data.data.roles === 'string') {
                    roles = [data.data.roles];
                } else if (typeof data.data.roles === 'object') {
                    // Extract values from object if roles is a JSON object with indices as keys
                    roles = Object.values(data.data.roles);
                }
            }

            console.log("Parsed roles:", roles);

            currentUser = {
                id: data.data.userId,
                username: data.data.username,
                roles: roles,
                verified: data.data.verified || false
            };

            localStorage.setItem('cdnToken', token);
            localStorage.setItem('cdnUser', JSON.stringify(currentUser));

            showMessage('Registered successfully!');
            initializeApp();
        } else {
            // Handle explicit error message from the server
            throw new Error(data.error || 'Registration failed');
        }
    } catch (error) {
        console.error("Registration error:", error);
        showMessage(error.message, true);
    } finally {
        button.disabled = false;
        button.classList.remove('loading');
    }
}

// Logout
function logout() {
    localStorage.removeItem('cdnToken');
    localStorage.removeItem('cdnUser');
    token = null;
    currentUser = null;
    csrfToken = '';
    showMessage('Logged out successfully!');
    initializeApp();
}

// Check verification status
async function checkVerificationStatus() {
    if (!token) return false;
    
    try {
        const response = await fetch('http://localhost:8080/api/auth/verification-status', {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        
        const data = await handleResponse(response);
        
        if (data.success && data.data && data.data.verified !== undefined) {
            return data.data.verified;
        }
        
        return false;
    } catch (error) {
        console.error('Error checking verification status:', error);
        return false;
    }
}

// Function to display verification banner if user is not verified
async function showVerificationStatusBanner() {
    if (!token || !currentUser) return;
    
    // If user is already verified according to localStorage, don't show banner
    if (currentUser.verified === true) {
        console.log("User is already verified in localStorage, skipping banner");
        
        // Remove any existing verification banner
        const existingBanner = document.querySelector('.verification-banner');
        if (existingBanner) {
            existingBanner.remove();
        }
        
        return;
    }
    
    // Remove any existing verification banner first
    const existingBanner = document.querySelector('.verification-banner');
    if (existingBanner) {
        existingBanner.remove();
    }
    
    const isVerified = await checkVerificationStatus();
    
    // Store in user object and localStorage
    if (currentUser) {
        currentUser.verified = isVerified;
        localStorage.setItem('cdnUser', JSON.stringify(currentUser));
    }
    
    // Show banner only if not verified
    if (!isVerified) {
        const banner = document.createElement('div');
        banner.className = 'verification-banner';
        banner.innerHTML = `
            <div class="verification-message">
                <i class="fas fa-exclamation-triangle"></i>
                <span>Your email is not verified. Please check your inbox or</span>
                <button id="resend-verification-btn" class="btn-link">resend verification email</button>
            </div>
            <button id="close-verification-banner" class="close-button">&times;</button>
        `;
        
        document.body.insertBefore(banner, document.body.firstChild);
        
        // Add event listeners
        document.getElementById('resend-verification-btn').addEventListener('click', resendVerificationEmail);
        document.getElementById('close-verification-banner').addEventListener('click', function() {
            banner.style.display = 'none';
        });
        
        // Add banner styles
        const style = document.createElement('style');
        style.textContent = `
            .verification-banner {
                background-color: #fff3cd;
                color: #856404;
                padding: 10px 20px;
                display: flex;
                justify-content: space-between;
                align-items: center;
                border-bottom: 1px solid #ffeeba;
                position: relative;
                z-index: 1000;
            }
            
            .verification-message {
                display: flex;
                align-items: center;
                gap: 10px;
            }
            
            .btn-link {
                background: none;
                border: none;
                padding: 0;
                margin: 0;
                font: inherit;
                color: var(--primary-color);
                text-decoration: underline;
                cursor: pointer;
            }
            
            .close-button {
                background: none;
                border: none;
                color: #856404;
                font-size: 1.2rem;
                cursor: pointer;
            }
        `;
        document.head.appendChild(style);
    } else {
        console.log("User email is already verified, no banner needed");
    }
}

// Function to resend verification email
async function resendVerificationEmail() {
    if (!token) {
        showMessage('Please login first!', true);
        return;
    }
    
    try {
        const resendButton = document.getElementById('resend-verification-btn');
        if (resendButton) {
            resendButton.disabled = true;
            resendButton.textContent = 'Sending...';
        }
        
        // Get CSRF token if not already present
        if (!csrfToken) {
            await getCsrfToken();
        }
        
        const response = await fetch('http://localhost:8080/api/auth/resend-verification', {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
                'X-XSRF-TOKEN': csrfToken
            },
            credentials: 'include'
        });
        
        const data = await handleResponse(response);
        
        if (data.success) {
            showMessage('Verification email sent. Please check your inbox.');
        } else {
            throw new Error(data.error || 'Failed to send verification email');
        }
    } catch (error) {
        console.error('Error resending verification email:', error);
        showMessage(error.message, true);
    } finally {
        const resendButton = document.getElementById('resend-verification-btn');
        if (resendButton) {
            resendButton.disabled = false;
            resendButton.textContent = 'resend verification email';
        }
    }
}

// Function to verify email from URL
async function verifyEmailFromUrl() {
    // Check if there's a token in the URL
    const urlParams = new URLSearchParams(window.location.search);
    const verificationToken = urlParams.get('token');
    
    if (!verificationToken) return false;
    
    // Show the verification section
    showSection('verification-section');
    
    try {
        const response = await fetch('http://localhost:8080/api/auth/verify', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ token: verificationToken })
        });
        
        const data = await handleResponse(response);
        const verificationStatus = document.getElementById('verification-status');
        
        if (data.success) {
            // Update UI for success
            verificationStatus.innerHTML = `
                <div class="verification-success">
                    <i class="fas fa-check-circle"></i>
                    <h3>Email Verified Successfully</h3>
                    <p>Your email has been verified. You can now enjoy all features of our platform.</p>
                    <div class="verification-actions">
                        <button onclick="showSection('login-section')" class="btn-primary">
                            <i class="fas fa-sign-in-alt"></i> Go to Login
                        </button>
                    </div>
                </div>
            `;
            
            // If the user is already logged in, update their verification status
            if (token && currentUser) {
                currentUser.verified = true;
                localStorage.setItem('cdnUser', JSON.stringify(currentUser));
                
                // Remove verification banner if it exists
                const banner = document.querySelector('.verification-banner');
                if (banner) {
                    banner.remove();
                }
                
                // Refresh the roles display to show verified badge
                displayRoles(currentUser.roles);
            }
            
            return true;
        } else {
            // Update UI for failure
            verificationStatus.innerHTML = `
                <div class="verification-error">
                    <i class="fas fa-times-circle"></i>
                    <h3>Verification Failed</h3>
                    <p>${data.error || 'Invalid or expired verification token. Please request a new verification email.'}</p>
                    <div class="verification-actions">
                        <button onclick="showSection('login-section')" class="btn-primary">
                            <i class="fas fa-sign-in-alt"></i> Go to Login
                        </button>
                    </div>
                </div>
            `;
            
            return false;
        }
    } catch (error) {
        console.error('Error verifying email:', error);
        
        // Update UI for error
        const verificationStatus = document.getElementById('verification-status');
        verificationStatus.innerHTML = `
            <div class="verification-error">
                <i class="fas fa-exclamation-triangle"></i>
                <h3>Verification Error</h3>
                <p>An error occurred while verifying your email: ${error.message}</p>
                <div class="verification-actions">
                    <button onclick="showSection('login-section')" class="btn-primary">
                        <i class="fas fa-sign-in-alt"></i> Go to Login
                    </button>
                </div>
            </div>
        `;
        
        return false;
    }
}