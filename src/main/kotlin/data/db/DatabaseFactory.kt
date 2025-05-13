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

        // Check if database is already initialized
        val tablesExist = transaction(database) {
            try {
                val result = exec("SELECT COUNT(*) > 0 FROM file_meta") { rs ->
                    rs.next() && rs.getBoolean(1)
                }
                println("Database check: file_meta table exists = $result")
                result ?: false
            } catch (e: Exception) {
                println("Database check: file_meta table doesn't exist or is empty")
                false
            }
        }

        // Apply migrations FIRST with direct SQL
        forceApplyMigrations(database)

        // Create or update tables
        transaction(database) {
            // Users and authorization tables are handled by DatabaseInit
            // We only create the file-related tables here
            if (tablesExist) {
                // Instead of using createMissingTablesAndColumns, just check for missing columns manually
                // This avoids constraint conflicts
                try {
                    exec("ALTER TABLE file_meta ADD COLUMN IF NOT EXISTS is_public BOOLEAN DEFAULT FALSE")
                    exec("ALTER TABLE file_meta ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(20) DEFAULT 'PENDING'")
                    println("File metadata table columns checked")
                } catch (e: Exception) {
                    println("Error checking file_meta columns: ${e.message}")
                }
            } else {
                // Create tables from scratch
                SchemaUtils.create(FileMetaTable)
                println("File metadata tables created")
            }
        }
    }

    // Apply migrations with direct SQL to avoid issues with missing columns
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

                // Add verification column to users table - but don't check for it yet
                try {
                    exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS verified BOOLEAN DEFAULT FALSE")
                    println("Added or verified 'verified' column exists in users table")
                } catch (e: Exception) {
                    println("Note: Could not add verified column, it may already exist: ${e.message}")
                }

                // Create tokens table if not exists
                try {
                    exec("""
                        CREATE TABLE IF NOT EXISTS tokens (
                            token VARCHAR(64) PRIMARY KEY,
                            user_id VARCHAR(36) NOT NULL,
                            type VARCHAR(20) NOT NULL,
                            expires_at BIGINT NOT NULL,
                            used BOOLEAN DEFAULT FALSE,
                            created_at BIGINT DEFAULT 0
                        )
                    """)
                    println("Created or verified tokens table exists")
                } catch (e: Exception) {
                    println("Error creating tokens table: ${e.message}")
                }

                // Add foreign key later to avoid errors if users table doesn't exist yet
                try {
                    exec("""
                        ALTER TABLE tokens ADD CONSTRAINT fk_tokens_user_id 
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    """)
                } catch (e: Exception) {
                    println("Could not add foreign key constraint to tokens table: ${e.message}")
                }

                // If file_meta table has column issues, recreate it
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