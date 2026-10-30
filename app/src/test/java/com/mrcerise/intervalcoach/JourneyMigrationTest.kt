package com.mrcerise.intervalcoach

import com.mrcerise.intervalcoach.data.db.PulseMigrations
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class JourneyMigrationTest {
    private fun schema(version: Int) = Json.parseToJsonElement(
        File("schemas/com.mrcerise.intervalcoach.data.db.PulseDatabase/$version.json").readText()
    ).jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray.map { it.jsonObject }

    private fun create(db: Connection, tables: List<JsonObject>) {
        tables.forEach { table ->
            val name = table["tableName"]!!.jsonPrimitive.content
            db.createStatement().use { statement ->
                statement.execute(table["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                table["indices"]?.jsonArray?.forEach {
                    statement.execute(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                }
            }
        }
    }

    private fun columns(db: Connection, table: String): List<String> {
        val result = mutableListOf<String>()
        db.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table)").use { cursor ->
                while (cursor.next()) result += listOf("name", "type", "notnull", "dflt_value", "pk")
                    .joinToString(":") { cursor.getString(it).orEmpty() }
            }
        }
        return result
    }

    @Test fun migrationMatchesFreshRoomSchemaAndPreservesLegacySessions() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { migrated ->
            DriverManager.getConnection("jdbc:sqlite::memory:").use { fresh ->
                create(migrated, schema(3))
                create(fresh, schema(4))
                migrated.createStatement().use { statement ->
                    statement.execute("""INSERT INTO sessions (id, workoutId, workoutName, workoutSnapshotJson,
                        startedAt, endedAt, activeMillis, wallMillis, sessionElapsedMillis, completedIntervals,
                        totalIntervals, skippedIntervals, completionPercent, status, roundsLogged)
                        VALUES ('legacy', 'workout', 'My workout', '{}', 1, 2, 60000, 60000, 60000, 1, 1, 0, 100, 'COMPLETED', 0)""")
                    PulseMigrations.THREE_TO_FOUR.forEach(statement::execute)
                    statement.executeQuery("SELECT source, journeyNodeId, activeMillis FROM sessions WHERE id = 'legacy'").use {
                        assertTrue(it.next())
                        assertEquals("FREE", it.getString("source"))
                        assertNull(it.getString("journeyNodeId"))
                        assertEquals(60000L, it.getLong("activeMillis"))
                    }
                }
                schema(4).forEach {
                    val table = it["tableName"]!!.jsonPrimitive.content
                    assertEquals("Schema mismatch in $table", columns(fresh, table), columns(migrated, table))
                }
            }
        }
    }
}
