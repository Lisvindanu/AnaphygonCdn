// src/main/kotlin/org/anaphygon/auth/db/DatabaseInit.kt
package org.anaphygon.auth.db

import kotlinx.coroutines.runBlocking
import org.anaphygon.auth.model.Permission
import org.anaphygon.auth.service.UserRoleService
import org.anaphygon.util.Logger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

object DatabaseInit {
    private val logger = Logger("DatabaseInit")

    fun init(database: Database) {
        logger.info("Initializing database tables and default data")

        transaction(database) {
            // Create tables
            SchemaUtils.create(
                UsersTable,
                RolesTable,
                UserRolesTable,
                PermissionsTable,
                RolePermissionsTable
            )

            // Create default permissions
            createDefaultPermissions()

            // Create default roles
            createDefaultRoles()

            // Create default admin user if needed
            createDefaultAdminUser()
            
            // Create specific admin user
            createSpecificAdminUser("lisvindanu015@gmail.com", "Lisvindanu", "Lisvindanu")
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

            UsersTable.insert {
                it[id] = adminId
                it[username] = "admin"
                it[email] = "admin@example.com"
                it[passwordHash] = hashedPassword
                it[active] = true
                it[createdAt] = System.currentTimeMillis()
            }

            // Assign admin role
            UserRolesTable.insert {
                it[userId] = adminId
                it[roleName] = "ADMIN"
            }

            logger.info("Created default admin user (username: admin, password: admin)")
        }
    }
    
    private fun createSpecificAdminUser(email: String, username: String, password: String) {
        // Check if the specific admin user already exists
        val userCount = UsersTable.selectAll()
            .where { UsersTable.email eq email }
            .count()
            
        if (userCount == 0L) {
            // Create specific admin user
            val userId = UUID.randomUUID().toString()
            val hashedPassword = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
            
            UsersTable.insert {
                it[id] = userId
                it[this.username] = username
                it[this.email] = email
                it[passwordHash] = hashedPassword
                it[active] = true
                it[createdAt] = System.currentTimeMillis()
            }
            
            // Assign admin role
            UserRolesTable.insert {
                it[this.userId] = userId
                it[roleName] = "ADMIN"
            }
            
            // Verify role assignment
            val roleCount = UserRolesTable.selectAll()
                .where { (UserRolesTable.userId eq userId) and (UserRolesTable.roleName eq "ADMIN") }
                .count()
                
            logger.info("Created specific admin user (email: $email, username: $username, role assigned: ${roleCount > 0})")
        } else {
            // User already exists, check if they have admin role
            val existingUser = UsersTable.selectAll()
                .where { UsersTable.email eq email }
                .firstOrNull()
                
            if (existingUser != null) {
                val userId = existingUser[UsersTable.id]
                
                // Check if user has admin role
                val hasAdminRole = UserRolesTable.selectAll()
                    .where { (UserRolesTable.userId eq userId) and (UserRolesTable.roleName eq "ADMIN") }
                    .count() > 0
                    
                if (!hasAdminRole) {
                    // Add admin role
                    UserRolesTable.insert {
                        it[this.userId] = userId
                        it[roleName] = "ADMIN"
                    }
                    logger.info("Added ADMIN role to existing user: $email")
                } else {
                    logger.info("Specific admin user already exists (email: $email, username: ${existingUser[UsersTable.username]})")
                }
            }
        }
    }
}