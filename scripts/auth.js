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
                roles: roles
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
                roles: roles
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