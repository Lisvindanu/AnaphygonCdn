package org.anaphygon.auth.db

import org.jetbrains.exposed.sql.Table

object TokensTable : Table("tokens") {
    val token = varchar("token", 64)
    val userId = varchar("user_id", 36) references UsersTable.id
    val type = varchar("type", 20) // "VERIFICATION" or "PASSWORD_RESET"
    val expiresAt = long("expires_at")
    val used = bool("used").default(false)
    val createdAt = long("created_at").default(System.currentTimeMillis())

    override val primaryKey = PrimaryKey(token)

    init {
        index(true, token, type) // Create an index on token and type
    }
}