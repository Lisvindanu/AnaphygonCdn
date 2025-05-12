package org.anaphygon.data.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.anaphygon.data.model.FileMeta

object FileMetaTable : Table("file_meta") {
    val id: Column<String> = varchar("id", 36)
    val fileName: Column<String> = varchar("file_name", 255)
    val storedFileName: Column<String> = varchar("stored_file_name", 255)
    val contentType: Column<String> = varchar("content_type", 100)
    val size: Column<Long> = long("size")
    val uploadDate: Column<Long> = long("upload_date")

    override val primaryKey = PrimaryKey(id)

    fun toFileMeta(row: ResultRow): FileMeta {
        return FileMeta(
            id = row[id],
            fileName = row[fileName],
            storedFileName = row[storedFileName],
            contentType = row[contentType],
            size = row[size],
            uploadDate = row[uploadDate]
        )
    }
}