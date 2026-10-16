package com.ggym.app.testing

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The schema snapshots the build exports under `app/schemas/`, read as SQL.
 *
 * A committed snapshot is the only faithful description of what a shipped schema version looked
 * like, which is what a migration test needs to build a database of the *older* version. Re-stating
 * the v1 `CREATE TABLE` statements by hand would test the test, not the app.
 */
internal object SchemaSnapshots {

    /**
     * Gradle runs unit tests with the module directory as the working directory, so the snapshots
     * sit at `schemas/…`. The `app/schemas` fallback keeps the helper working when a runner starts
     * from the repository root instead.
     */
    fun directory(): File {
        val candidates = listOf(File("schemas"), File("app/schemas"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("could not find the exported schema directory from ${File(".").absolutePath}")
    }

    /** The path a committed snapshot of [version] must occupy. */
    fun file(version: Int): File = File(directory(), "com.ggym.app.data.room.GgymDatabase/$version.json")

    /**
     * Every `CREATE TABLE` and `CREATE INDEX` statement of [version], in dependency order, with the
     * `${TABLE_NAME}` placeholder a Room snapshot uses filled in.
     */
    fun statements(version: Int): List<String> {
        val file = file(version)
        check(file.isFile) { "the schema snapshot of version $version must be committed at ${file.absolutePath}" }
        val schema = SchemaJson.decodeFromString<SchemaFile>(file.readText())
        check(schema.database.version == version) {
            "the snapshot at ${file.absolutePath} claims to be version ${schema.database.version}"
        }
        return schema.database.entities.flatMap { entity ->
            buildList {
                add(entity.createSql.forTable(entity.tableName))
                entity.indices.forEach { add(it.createSql.forTable(entity.tableName)) }
            }
        }
    }

    private fun String.forTable(table: String): String = replace("\${TABLE_NAME}", table)

    private val SchemaJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class SchemaFile(val database: SchemaDatabase)

    @Serializable
    private data class SchemaDatabase(val version: Int, val entities: List<SchemaEntity>)

    @Serializable
    private data class SchemaEntity(
        val tableName: String,
        val createSql: String,
        val indices: List<SchemaIndex> = emptyList(),
    )

    @Serializable
    private data class SchemaIndex(val createSql: String)
}
