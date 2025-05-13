// src/main/kotlin/org/anaphygon/auth/db/DatabaseInit.kt
package org.anaphygon.auth.db

import kotlinx.coroutines.runBlocking
import org.anaphygon.auth.model.Permission
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.util.Logger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.statements.jdbc.JdbcConnectionImpl
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.util.*

object DatabaseInit {
    private val logger = Logger("DatabaseInit")

    // Improved helper function for executing raw SQL
    private fun <T> exec(
        sql: String,
        parameters: List<Any?> = emptyList(),
        transform: ((ResultSet) -> T)? = null
    ): T? {
        val connection = (TransactionManager.current().connection as JdbcConnectionImpl).connection
        
        return connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { stmt ->
            // Set parameters
            parameters.forEachIndexed { index, value ->
                stmt.setObject(index + 1, value)
            }
            
            // Execute the statement
            if (transform != null) {
                val rs = stmt.executeQuery()
                rs.use { transform(it) }
            } else {
                stmt.executeUpdate()
                null
            }
        }
    }

    fun init(database: Database) {
        logger.info("Initializing database tables and default data")

        transaction(database) {
            // Check if database is already initialized
            val databaseInitialized = try {
                exec<Boolean?>("SELECT COUNT(*) > 0 FROM users") { rs ->
                    rs.next() && rs.getBoolean(1)
                } ?: false
            } catch (e: Exception) {
                logger.info("Database appears to be empty or not initialized")
                false
            }

            if (databaseInitialized) {
                logger.info("Database already initialized, skipping default data creation")
                
                // Just make sure the schema is up to date
                // Add tables in right order - first make sure verified exists in user table schema
                try {
                    exec<Unit>("ALTER TABLE users ADD COLUMN IF NOT EXISTS verified BOOLEAN DEFAULT FALSE")
                    logger.info("Checked verified column in users table")
                } catch (e: Exception) {
                    logger.warn("Note: ${e.message}")
                }

                // Create tokens table if needed
                try {
                    exec<Unit>("""
                        CREATE TABLE IF NOT EXISTS tokens (
                            token VARCHAR(64) PRIMARY KEY,
                            user_id VARCHAR(36) NOT NULL,
                            type VARCHAR(20) NOT NULL,
                            expires_at BIGINT NOT NULL,
                            used BOOLEAN DEFAULT FALSE,
                            created_at BIGINT DEFAULT 0
                        )
                    """)
                    logger.info("Checked tokens table exists")
                } catch (e: Exception) {
                    logger.warn("Note: ${e.message}")
                }

                // Skip the automatic schema validation since tables already exist
                // This avoids the constraint conflicts
                logger.info("Skipping schema validation for existing tables")
                
                return@transaction
            }

            // Add tables in right order - first make sure verified exists in user table schema
            try {
                exec<Unit>("ALTER TABLE users ADD COLUMN IF NOT EXISTS verified BOOLEAN DEFAULT FALSE")
                logger.info("Added or checked verified column in users table")
            } catch (e: Exception) {
                logger.warn("Note: ${e.message}")
            }

            // Create tokens table if needed
            try {
                exec<Unit>("""
                    CREATE TABLE IF NOT EXISTS tokens (
                        token VARCHAR(64) PRIMARY KEY,
                        user_id VARCHAR(36) NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        used BOOLEAN DEFAULT FALSE,
                        created_at BIGINT DEFAULT 0
                    )
                """)
                logger.info("Created or verified tokens table exists")
            } catch (e: Exception) {
                logger.warn("Note: ${e.message}")
            }

            // Create tables
            SchemaUtils.create(
                UsersTable,
                RolesTable,
                UserRolesTable,
                PermissionsTable,
                RolePermissionsTable,
                TokensTable
            )

            // Create default permissions
            createDefaultPermissions()

            // Create default roles
            createDefaultRoles()

            // Create default admin user if needed
            createDefaultAdminUser()

            // Create specific admin user
            try {
                createSpecificAdminUser("lisvindanu015@gmail.com", "Lisvindanu", "Lisvindanu")
            } catch (e: Exception) {
                logger.warn("Error creating specific admin user: ${e.message}")
            }
        }

        logger.info("Database initialization completed")
    }

    private fun createDefaultPermissions() {
        Permission.values().forEach { permission ->
            val permCount = PermissionsTable.selectAll()
                .where { PermissionsTable.name eq permission.name }
                .count()

            if (permCount == 0L) {
                PermissionsTable.insert {
                    it[name] = permission.name
                    it[description] = permission.name
                        .replace("_", " ")
                        .lowercase()
                        .replaceFirstChar { char -> char.uppercase() }
                }
                logger.info("Created permission: ${permission.name}")
            }
        }
    }

    private fun createDefaultRoles() {
        // Admin role with all permissions
        val adminRoleCount = RolesTable.selectAll()
            .where { RolesTable.name eq "ADMIN" }
            .count()

        if (adminRoleCount == 0L) {
            RolesTable.insert {
                it[name] = "ADMIN"
                it[description] = "Administrator with full access"
                it[createdAt] = System.currentTimeMillis()
            }

            Permission.values().forEach { permission ->
                RolePermissionsTable.insert {
                    it[roleName] = "ADMIN"
                    it[permissionName] = permission.name
                }
            }
            logger.info("Created ADMIN role with all permissions")
        }

        // Regular user role with basic permissions
        val userRoleCount = RolesTable.selectAll()
            .where { RolesTable.name eq "USER" }
            .count()

        if (userRoleCount == 0L) {
            RolesTable.insert {
                it[name] = "USER"
                it[description] = "Regular user with basic access"
                it[createdAt] = System.currentTimeMillis()
            }

            val userPermissions = listOf(
                Permission.VIEW_FILES,
                Permission.UPLOAD_FILES,
                Permission.VIEW_FILE_HISTORY
            )

            userPermissions.forEach { permission ->
                RolePermissionsTable.insert {
                    it[roleName] = "USER"
                    it[permissionName] = permission.name
                }
            }
            logger.info("Created USER role with basic permissions")
        }
    }

    private fun createDefaultAdminUser() {
        // Check if admin user already exists
        val adminCount = UsersTable.selectAll()
            .where { UsersTable.username eq "admin" }
            .count()

        if (adminCount == 0L) {
            // Create admin user
            val adminId = UUID.randomUUID().toString()
            val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw("admin", org.mindrot.jbcrypt.BCrypt.gensalt())

            // Use direct SQL insert with all fields
            exec<Unit>("""
                INSERT INTO users (id, username, email, password_hash, active, created_at, verified) 
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
                listOf(
                    adminId,
                    "admin",
                    "admin@example.com",
                    hashedPassword,
                    true,
                    System.currentTimeMillis(),
                    true
                ))

            // Assign admin role
            UserRolesTable.insert {
                it[userId] = adminId
                it[roleName] = "ADMIN"
            }

            logger.info("Created default admin user (username: admin, password: admin)")
        }
    }

    private fun createSpecificAdminUser(email: String, username: String, password: String) {
        try {
            // Get the user by email using direct SQL to avoid verified column issues
            val userId = exec<String?>("""
                SELECT id FROM users WHERE email = ?
            """, listOf(email)) { rs ->
                if (rs.next()) rs.getString("id") else null
            }

            if (userId == null) {
                // Create specific admin user
                val newUserId = UUID.randomUUID().toString()
                val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())

                // Use direct SQL insert with all fields
                exec<Unit>("""
                    INSERT INTO users (id, username, email, password_hash, active, created_at, verified) 
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                    listOf(
                        newUserId,
                        username,
                        email,
                        hashedPassword,
                        true,
                        System.currentTimeMillis(),
                        true
                    ))

                // Assign admin role
                UserRolesTable.insert {
                    it[this.userId] = newUserId
                    it[roleName] = "ADMIN"
                }

                logger.info("Created specific admin user (email: $email, username: $username)")
            } else {
                // User exists, check if they have admin role
                val hasAdminRole = exec<Boolean?>("""
                    SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_name = 'ADMIN'
                """, listOf(userId)) { rs ->
                    rs.next() && rs.getLong(1) > 0
                } ?: false

                if (!hasAdminRole) {
                    // Add admin role
                    UserRolesTable.insert {
                        it[this.userId] = userId
                        it[roleName] = "ADMIN"
                    }
                    logger.info("Added ADMIN role to existing user: $email")
                } else {
                    logger.info("User already has ADMIN role: $email")
                }
            }
        } catch (e: Exception) {
            logger.error("Error in createSpecificAdminUser: ${e.message}")
            throw e
        }
    }
}