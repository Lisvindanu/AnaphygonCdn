package org.anaphygon.data.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection

object DatabaseFactory {
    fun init(database: Database) {
        // Set default transaction isolation level
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        // Apply migrations BEFORE creating tables to ensure all columns exist
        forceApplyMigrations(database)

        // Create or update tables
        transaction(database) {
            SchemaUtils.create(FileMetaTable)
            println("Database tables created or validated successfully")
        }
    }

    // Force creation of the new columns using direct SQL statements
    private fun forceApplyMigrations(database: Database) {
        transaction(database) {
            try {
                // Add columns directly using SQL, ignoring errors if columns already exist
                try {
                    exec("ALTER TABLE file_meta ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT FALSE")
                    println("Added or verified is_public column exists")
                } catch (e: Exception) {
                    println("Note: Could not add is_public column, it may already exist: ${e.message}")
                }

                try {
                    exec("ALTER TABLE file_meta ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) DEFAULT 'PENDING'")
                    println("Added or verified moderation_status column exists")
                } catch (e: Exception) {
                    println("Note: Could not add moderation_status column, it may already exist: ${e.message}")
                }

                // Verify columns exist by trying to select from them
                val hasIsPublic = try {
                    exec("SELECT is_public FROM file_meta WHERE 1=0")
                    true
                } catch (e: Exception) {
                    false
                }

                val hasModerationStatus = try {
                    exec("SELECT moderation_status FROM file_meta WHERE 1=0")
                    true
                } catch (e: Exception) {
                    false
                }

                println("Column verification - is_public: $hasIsPublic, moderation_status: $hasModerationStatus")

                // If columns weren't added, try to recreate the whole table with all columns
                if (!hasIsPublic || !hasModerationStatus) {
                    println("WARNING: Migration failed, columns not added! Taking more drastic measures...")

                    // Try to rename the old table
                    try {
                        exec("ALTER TABLE file_meta RENAME TO file_meta_backup")

                        // Create a new table with all columns
                        exec("""
                            CREATE TABLE file_meta (
                                id VARCHAR(36) PRIMARY KEY,
                                file_name VARCHAR(255) NOT NULL,
                                stored_file_name VARCHAR(255) NOT NULL,
                                content_type VARCHAR(100) NOT NULL,
                                file_size BIGINT NOT NULL,
                                upload_date BIGINT NOT NULL,
                                user_id VARCHAR(36),
                                is_public BOOLEAN DEFAULT FALSE,
                                moderation_status VARCHAR(20) DEFAULT 'PENDING'
                            )
                        """)

                        // Copy data from backup table, filling in default values for new columns
                        exec("""
                            INSERT INTO file_meta (id, file_name, stored_file_name, content_type, file_size, upload_date, user_id, is_public, moderation_status)
                            SELECT id, file_name, stored_file_name, content_type, file_size, upload_date, user_id, 
                                   FALSE as is_public, 'PENDING' as moderation_status
                            FROM file_meta_backup
                        """)

                        println("Table recreated successfully with all required columns!")
                    } catch (e: Exception) {
                        println("Error during table recreation: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                println("Error during migration: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}