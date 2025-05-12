# Anaphygon CDN

A personal content delivery network service built with Ktor in Kotlin.

## Overview

This CDN provides a secure, efficient, and scalable solution for file storage and delivery. It supports various file types including images, documents, audio, and video files with on-demand transformations for image assets.

## Key Features

<details>
<summary><strong>File Management</strong></summary>

- File upload with validation
- Secure file storage
- File retrieval with efficient caching
- File metadata management
- File deletion
</details>

<details>
<summary><strong>Image Processing</strong></summary>

- On-demand image resizing
- Thumbnail generation
- Format conversion
</details>

<details>
<summary><strong>Security</strong></summary>

- JWT-based authentication
- CSRF protection
- Rate limiting to prevent abuse
- File type validation
</details>

<details>
<summary><strong>Monitoring</strong></summary>

- Comprehensive metrics collection
- Storage usage analytics
- Performance monitoring
- Request logging
</details>

<details>
<summary><strong>Performance Optimizations</strong></summary>

- Response caching
- Efficient file handling
- Proper HTTP headers for browser caching
</details>

<details>
<summary><strong>Database Integration</strong></summary>

- H2 database for metadata storage
- Clean separation of data and storage layers
</details>

<details>
<summary><strong>Other Features</strong></summary>

- CORS support for cross-origin requests
- Error handling with status pages
- Background cleanup job for removing orphaned files
</details>

## Architecture

<details>
<summary><strong>Click to expand</strong></summary>

The project follows a modular architecture:
- Routing layer for request handling
- Controller layer for business logic
- Service layer for file operations
- Database layer for metadata storage
- Utility classes for common operations
</details>

## Tech Stack

<details>
<summary><strong>Click to expand</strong></summary>

- **Framework**: Ktor
- **Language**: Kotlin
- **Database**: H2
- **Authentication**: JWT
- **ORM**: Exposed
- **Build Tool**: Gradle
</details>

## API Endpoints

<details>
<summary><strong>File Operations</strong></summary>

- `GET /api/files/{id}` - Retrieve a file
- `GET /api/files/{id}/thumbnail` - Get file thumbnail
- `GET /api/files/{id}/resize` - Resize an image
- `GET /api/files/{id}/info` - Get file metadata
- `POST /api/files` - Upload a file
- `DELETE /api/files/{id}` - Delete a file
</details>

<details>
<summary><strong>Authentication</strong></summary>

- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login to the system
- `GET /api/auth/csrf` - Get CSRF token
</details>

<details>
<summary><strong>Monitoring</strong></summary>

- `GET /metrics-micrometer` - Prometheus metrics
- `GET /api/metrics/storage` - Storage usage metrics
</details>

## Client Interface

<details>
<summary><strong>Click to expand</strong></summary>

The project includes an HTML/JS client interface for:
- User registration and login
- File upload and management
- File viewing and downloading
</details>