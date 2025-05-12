package org.anaphygon.auth.service

import kotlinx.coroutines.Dispatchers
import org.anaphygon.auth.db.* // Use the tables from the db package
import org.anaphygon.auth.model.Permission
import org.anaphygon.auth.model.Role
import org.anaphygon.auth.model.User
import org.anaphygon.util.Logger
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class UserRoleService(private val database: Database) {
    private val logger = Logger("UserRoleService")

    // Initialize database tables and default roles
    suspend fun initialize() {
        dbQuery {
            SchemaUtils.create(
                UsersTable,
                RolesTable,
                UserRolesTable,
                PermissionsTable,
                RolePermissionsTable,
                TokensTable // Add TokensTable to schema creation
            )

            // Create default permissions
            Permission.values().forEach { permission ->
                val permCount = PermissionsTable.selectAll().where { PermissionsTable.name eq permission.name }.count()
                if (permCount == 0L) {
                    PermissionsTable.insert {
                        it[name] = permission.name
                        it[description] = permission.name.replace("_", " ").lowercase().replaceFirstChar { char -> char.uppercase() }
                    }
                }
            }

            // Create default roles
            createDefaultRolesIfNotExist()

            // Create default admin user
            createDefaultAdminUserIfNotExist()
        }
    }

    private fun createDefaultRolesIfNotExist() {
        // Admin role with all permissions
        val adminRoleCount = RolesTable.selectAll().where { RolesTable.name eq "ADMIN" }.count()
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
        }

        // Regular user role with basic permissions
        val userRoleCount = RolesTable.selectAll().where { RolesTable.name eq "USER" }.count()
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
        }
    }

    private fun createDefaultAdminUserIfNotExist() {
        val adminCount = UsersTable.selectAll().where { UsersTable.username eq "admin" }.count()
        if (adminCount == 0L) {
            val adminId = UUID.randomUUID().toString()
            val hashedPassword = BCrypt.hashpw("admin", BCrypt.gensalt())

            UsersTable.insert {
                it[id] = adminId
                it[username] = "admin"
                it[email] = "admin@example.com"
                it[passwordHash] = hashedPassword
                it[active] = true
                it[verified] = true // Admin is automatically verified
                it[createdAt] = System.currentTimeMillis()
            }

            UserRolesTable.insert {
                it[userId] = adminId
                it[roleName] = "ADMIN"
            }

            logger.info("Created default admin user")
        }
    }

    suspend fun createUser(username: String, email: String, password: String): User {
        return dbQuery {
            try {
                // Check if username already exists
                val usernameExists = UsersTable.selectAll().where { UsersTable.username eq username }.count() > 0
                // Check if email already exists
                val emailExists = UsersTable.selectAll().where { UsersTable.email eq email }.count() > 0

                if (usernameExists || emailExists) {
                    throw IllegalArgumentException("Username or email already exists")
                }

                val userId = UUID.randomUUID().toString()
                val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())

                UsersTable.insert {
                    it[id] = userId
                    it[this.username] = username
                    it[this.email] = email
                    it[passwordHash] = hashedPassword
                    it[active] = true
                    it[verified] = false // New users start unverified
                    it[createdAt] = System.currentTimeMillis()
                }

                // Assign default USER role
                UserRolesTable.insert {
                    it[this.userId] = userId
                    it[roleName] = "USER"
                }

                // Fetch the created user with its roles
                val userRow = UsersTable.selectAll()
                    .where { UsersTable.id eq userId }
                    .firstOrNull()

                if (userRow == null) {
                    throw IllegalStateException("User was created but could not be retrieved")
                }

                mapRowToUser(userRow)
            } catch (e: Exception) {
                // Log error for debugging
                println("Error creating user: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun authenticateUser(usernameOrEmail: String, password: String): User? {
        return dbQuery {
            // Try to find by username
            var userRow = UsersTable.selectAll().where { UsersTable.username eq usernameOrEmail }.firstOrNull()

            // If not found by username, try by email
            if (userRow == null) {
                userRow = UsersTable.selectAll().where { UsersTable.email eq usernameOrEmail }.firstOrNull()
            }

            val user = userRow?.let { mapRowToUser(it) }

            if (user != null && BCrypt.checkpw(password, user.passwordHash)) {
                // Update last login time
                UsersTable.update({ UsersTable.id eq user.id }) {
                    it[lastLogin] = System.currentTimeMillis()
                }

                user
            } else {
                null
            }
        }
    }

    // Token creation for email verification
    suspend fun createVerificationToken(userId: String): String {
        return dbQuery {
            val token = UUID.randomUUID().toString().replace("-", "")
            val expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours

            // Delete any existing unused verification tokens for this user
            TokensTable.deleteWhere {
                (TokensTable.userId eq userId) and
                        (TokensTable.type eq "VERIFICATION") and
                        (TokensTable.used eq false)
            }

            // Create new token
            TokensTable.insert {
                it[TokensTable.token] = token
                it[TokensTable.userId] = userId
                it[TokensTable.type] = "VERIFICATION"
                it[TokensTable.expiresAt] = expiryTime
                it[TokensTable.createdAt] = System.currentTimeMillis()
            }

            logger.info("Created verification token for user $userId")
            token
        }
    }

    // Verify email with token
    suspend fun verifyEmail(token: String): Boolean {
        return dbQuery {
            try {
                // Find token
                val tokenRow = TokensTable
                    .selectAll()
                    .where {
                        (TokensTable.token eq token) and
                                (TokensTable.type eq "VERIFICATION") and
                                (TokensTable.used eq false) and
                                (TokensTable.expiresAt greater System.currentTimeMillis())
                    }
                    .firstOrNull() ?: return@dbQuery false

                // Mark token as used
                TokensTable.update({ TokensTable.token eq token }) {
                    it[used] = true
                }

                // Update user's verified status
                val userId = tokenRow[TokensTable.userId]
                val updated = UsersTable.update({ UsersTable.id eq userId }) {
                    it[verified] = true
                }

                logger.info("Email verified for user $userId: ${updated > 0}")
                updated > 0
            } catch (e: Exception) {
                logger.error("Error verifying email: ${e.message}", e)
                false
            }
        }
    }

    // Check if user is verified
    suspend fun isUserVerified(userId: String): Boolean {
        return dbQuery {
            UsersTable
                .selectAll()
                .where { (UsersTable.id eq userId) and (UsersTable.verified eq true) }
                .count() > 0
        }
    }

    // Password reset token creation
    suspend fun createPasswordResetToken(userId: String): String {
        return dbQuery {
            val token = UUID.randomUUID().toString().replace("-", "")
            val expiryTime = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hour

            // Delete any existing unused password reset tokens for this user
            TokensTable.deleteWhere {
                (TokensTable.userId eq userId) and
                        (TokensTable.type eq "PASSWORD_RESET") and
                        (TokensTable.used eq false)
            }

            // Create new token
            TokensTable.insert {
                it[TokensTable.token] = token
                it[TokensTable.userId] = userId
                it[TokensTable.type] = "PASSWORD_RESET"
                it[TokensTable.expiresAt] = expiryTime
                it[TokensTable.createdAt] = System.currentTimeMillis()
            }

            logger.info("Created password reset token for user $userId")
            token
        }
    }

    // Reset password with token
    suspend fun resetPassword(token: String, newPassword: String): Boolean {
        return dbQuery {
            try {
                // Find token
                val tokenRow = TokensTable
                    .selectAll()
                    .where {
                        (TokensTable.token eq token) and
                                (TokensTable.type eq "PASSWORD_RESET") and
                                (TokensTable.used eq false) and
                                (TokensTable.expiresAt greater System.currentTimeMillis())
                    }
                    .firstOrNull() ?: return@dbQuery false

                // Mark token as used
                TokensTable.update({ TokensTable.token eq token }) {
                    it[used] = true
                }

                // Update user's password
                val userId = tokenRow[TokensTable.userId]
                val hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt())

                val updated = UsersTable.update({ UsersTable.id eq userId }) {
                    it[passwordHash] = hashedPassword
                }

                logger.info("Password reset for user $userId: ${updated > 0}")
                updated > 0
            } catch (e: Exception) {
                logger.error("Error resetting password: ${e.message}", e)
                false
            }
        }
    }

    suspend fun getUserById(id: String): User? {
        return dbQuery {
            UsersTable.selectAll().where { UsersTable.id eq id }
                .firstOrNull()
                ?.let { mapRowToUser(it) }
        }
    }

    suspend fun getUserByUsername(username: String): User? {
        return dbQuery {
            UsersTable.selectAll().where { UsersTable.username eq username }
                .firstOrNull()
                ?.let { mapRowToUser(it) }
        }
    }

    suspend fun getUserByEmail(email: String): User? {
        return dbQuery {
            UsersTable.selectAll().where { UsersTable.email eq email }
                .firstOrNull()
                ?.let { mapRowToUser(it) }
        }
    }

    suspend fun getAllUsers(): List<User> {
        return dbQuery {
            UsersTable.selectAll()
                .map { mapRowToUser(it) }
        }
    }

    suspend fun getAllRoles(): List<Role> {
        return dbQuery {
            RolesTable.selectAll().map { resultRow ->
                val roleName = resultRow[RolesTable.name]
                val permissions = RolePermissionsTable
                    .selectAll().where { RolePermissionsTable.roleName eq roleName }
                    .map { Permission.valueOf(it[RolePermissionsTable.permissionName]) }
                    .toSet()

                Role(
                    name = roleName,
                    description = resultRow[RolesTable.description],
                    permissions = permissions
                )
            }
        }
    }

    suspend fun getRole(name: String): Role? {
        return dbQuery {
            val roleRow = RolesTable.selectAll().where { RolesTable.name eq name }
                .firstOrNull() ?: return@dbQuery null

            val permissions = RolePermissionsTable
                .selectAll().where { RolePermissionsTable.roleName eq name }
                .map { Permission.valueOf(it[RolePermissionsTable.permissionName]) }
                .toSet()

            Role(
                name = roleRow[RolesTable.name],
                description = roleRow[RolesTable.description],
                permissions = permissions
            )
        }
    }

    suspend fun getUserPermissions(userId: String): Set<Permission> {
        return dbQuery {
            val userRoles = UserRolesTable
                .selectAll().where { UserRolesTable.userId eq userId }
                .map { it[UserRolesTable.roleName] }

            if (userRoles.isEmpty()) {
                return@dbQuery emptySet()
            }

            val permissions = mutableSetOf<Permission>()

            // Handle each role separately
            userRoles.forEach { roleName ->
                val rolePermissions = RolePermissionsTable
                    .selectAll().where { RolePermissionsTable.roleName eq roleName }
                    .map { Permission.valueOf(it[RolePermissionsTable.permissionName]) }

                permissions.addAll(rolePermissions)
            }

            permissions
        }
    }

    suspend fun hasPermission(userId: String, permission: Permission): Boolean {
        val permissions = getUserPermissions(userId)
        return permissions.contains(permission)
    }

    suspend fun assignRoleToUser(userId: String, roleName: String): Boolean {
        return dbQuery {
            // Check if role exists
            val roleExists = RolesTable.selectAll().where { RolesTable.name eq roleName }
                .count() > 0
            if (!roleExists) return@dbQuery false

            // Check if user exists
            val userExists = UsersTable.selectAll().where { UsersTable.id eq userId }
                .count() > 0
            if (!userExists) return@dbQuery false

            // Check if user already has this role
            val roleAlreadyAssigned = UserRolesTable.selectAll()
                .where {
                    UserRolesTable.userId eq userId and (UserRolesTable.roleName eq roleName)
                }
                .count() > 0

            if (roleAlreadyAssigned) {
                return@dbQuery true
            }

            UserRolesTable.insert {
                it[UserRolesTable.userId] = userId
                it[UserRolesTable.roleName] = roleName
            }

            true
        }
    }

    suspend fun removeRoleFromUser(userId: String, roleName: String): Boolean {
        return dbQuery {
            val deleted = UserRolesTable.deleteWhere {
                (UserRolesTable.userId eq userId) and (UserRolesTable.roleName eq roleName)
            }
            deleted > 0
        }
    }

    private fun mapRowToUser(row: ResultRow): User {
        val userId = row[UsersTable.id]

        // Get user roles
        val roles = UserRolesTable
            .selectAll().where { UserRolesTable.userId eq userId }
            .map { it[UserRolesTable.roleName] }
            .toSet()

        // Log roles for debugging
        println("User roles for ${row[UsersTable.username]}: $roles")

        return User(
            id = userId,
            username = row[UsersTable.username],
            email = row[UsersTable.email],
            passwordHash = row[UsersTable.passwordHash],
            roles = roles,
            active = row[UsersTable.active],
            verified = row[UsersTable.verified], // Include verified status
            createdAt = row[UsersTable.createdAt],
            lastLogin = row[UsersTable.lastLogin]
        )
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO, database) { block() }
}