package org.anaphygon.auth.db

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = varchar("id", 36).uniqueIndex()
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val active = bool("active").default(true)
    val createdAt = long("created_at")
    val lastLogin = long("last_login").nullable()
    val verified = bool("verified").default(false)  // Make sure this field exists

    override val primaryKey = PrimaryKey(id)
}

object RolesTable : Table("roles") {
    val name = varchar("name", 50)
    val description = varchar("description", 255)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(name)
}

object UserRolesTable : Table("user_roles") {
    val userId = varchar("user_id", 36) references UsersTable.id
    val roleName = varchar("role_name", 50) references RolesTable.name

    override val primaryKey = PrimaryKey(arrayOf(userId, roleName))
}

object PermissionsTable : Table("permissions") {
    val name = varchar("name", 50)
    val description = varchar("description", 255)

    override val primaryKey = PrimaryKey(name)
}

object RolePermissionsTable : Table("role_permissions") {
    val roleName = varchar("role_name", 50) references RolesTable.name
    val permissionName = varchar("permission_name", 50) references PermissionsTable.name

    override val primaryKey = PrimaryKey(arrayOf(roleName, permissionName))
}