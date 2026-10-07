package com.pulse.intervalcoach

import com.pulse.intervalcoach.data.db.PulseMigrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Migration test executed on the JVM against a real SQLite database (sqlite-jdbc).
 *
 * How the v1 database is obtained: Room exports the authoritative schema of the current version to
 * `app/schemas/…/2.json` on every build. The v1 schema is that schema with exactly the columns that
 * migration 1 → 2 adds removed — i.e. what a v1 database contained. The statements executed here are
 * the production constant [PulseMigrations.ONE_TO_TWO], so this exercises the shipped SQL.
 *
 * Coverage: acceptance case 13 — upgrading must preserve user workouts, favourites and session
 * history rather than dropping or resetting them.
 */
class DatabaseMigrationTest {

    private val schemaFile = File("schemas/com.pulse.intervalcoach.data.db.PulseDatabase/2.json")

    /** The columns migration 1 → 2 adds, and the table each one belongs to. */
    private val addedColumns = mapOf(
        "sessions" to "lastHeartbeatAtMillis",
        "workouts" to "builtIn",
    )

    private data class Table(val name: String, val createSql: String, val indices: List<String>)

    private fun readSchema(): List<Table> {
        assertTrue(
            "Room schema export missing at ${schemaFile.absolutePath} — run ./gradlew :app:assembleDebug first",
            schemaFile.exists(),
        )
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val entities = root["database"]!!.jsonObject["entities"]!!.jsonArray
        return entities.map { entity ->
            val obj = entity.jsonObject
            val name = obj["tableName"]!!.jsonPrimitive.content
            val createSql = obj["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name)
            val indices = obj["indices"]?.jsonArray?.map {
                it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", name)
            } ?: emptyList()
            Table(name, createSql, indices)
        }
    }

    /** Removes a column definition (and its separating comma) from a CREATE TABLE statement. */
    private fun stripColumn(sql: String, column: String): String {
        val definition = Regex("`$column`\\s+[A-Za-z]+(?:\\s*\\([^)]*\\))?(?:\\s+NOT NULL)?(?:\\s+DEFAULT\\s+[^,)]+)?")
            .find(sql)
            ?.value
            ?: error("column `$column` not found in $sql")
        val stripped = sql.replace(", $definition", "")
        assertTrue("v1 schema must no longer declare `$column`", !stripped.contains("`$column`"))
        return stripped
    }

    private fun Connection.createV1Database(tables: List<Table>) {
        tables.forEach { table ->
            val v1Sql = addedColumns[table.name]
                ?.let { column -> stripColumn(table.createSql, column) }
                ?: table.createSql
            createStatement().use { it.execute(v1Sql) }
            table.indices.forEach { index -> createStatement().use { it.execute(index) } }
        }
    }

    private fun Connection.columnNames(table: String): Set<String> {
        val columns = mutableSetOf<String>()
        prepareStatement("PRAGMA table_info(`$table`)").use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) columns += rs.getString("name")
            }
        }
        return columns
    }

    @Test
    fun `migration 1 to 2 preserves workouts sessions and events and adds the new columns`() {
        val tables = readSchema()
        assertTrue("expected the full entity set in the exported schema", tables.size >= 10)

        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV1Database(tables)

            // v1 guarantees: the new columns must not exist yet.
            assertTrue(!connection.columnNames("workouts").contains("builtIn"))
            assertTrue(!connection.columnNames("sessions").contains("lastHeartbeatAtMillis"))

            // Realistic pre-migration user data.
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO folders (id, name, sortOrder, createdAt) VALUES ('f1', 'Morning', 0, 900)
                    """.trimIndent()
                )
                statement.execute(
                    """
                    INSERT INTO workouts (id, name, description, type, planJson, folderId, iconKey, colorArgb,
                        equipment, voiceProfileId, isFavorite, createdAt, updatedAt, revision, lastUsedAt, useCount)
                    VALUES ('w1', 'Tabata Classic', 'Eight rounds', 'TABATA', '{"id":"w1"}', 'f1', 'tabata', NULL,
                        NULL, NULL, 1, 1000, 2000, 3, 1500, 7)
                    """.trimIndent()
                )
                statement.execute(
                    """
                    INSERT INTO tags (id, name) VALUES ('t1', 'HIIT')
                    """.trimIndent()
                )
                statement.execute("INSERT INTO workout_tags (workoutId, tagId) VALUES ('w1', 't1')")
                statement.execute(
                    """
                    INSERT INTO sessions (id, workoutId, workoutName, workoutSnapshotJson, startedAt, endedAt,
                        activeMillis, wallMillis, sessionElapsedMillis, completedIntervals, totalIntervals,
                        skippedIntervals, completionPercent, status, roundsLogged, notes, weightUnitAtRun)
                    VALUES ('s1', 'w1', 'Tabata Classic', '{"id":"w1"}', 1000, 2000, 240000, 260000, 240000,
                        16, 16, 0, 100, 'COMPLETED', 0, 'felt good', 'KG')
                    """.trimIndent()
                )
                statement.execute(
                    """
                    INSERT INTO session_events (sessionId, stepIndex, stepName, kind, atMillis, durationMillis, detail)
                    VALUES ('s1', 0, 'Work', 'INTERVAL_STARTED', 0, 0, NULL),
                           ('s1', 0, 'Work', 'INTERVAL_COMPLETED', 20000, 20000, 'EXPIRED')
                    """.trimIndent()
                )
            }

            // The production migration, statement for statement.
            connection.createStatement().use { statement ->
                PulseMigrations.ONE_TO_TWO.forEach(statement::execute)
            }

            // New columns exist…
            assertTrue(connection.columnNames("workouts").contains("builtIn"))
            assertTrue(connection.columnNames("sessions").contains("lastHeartbeatAtMillis"))

            // …and every row written before the upgrade survived intact.
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT name, isFavorite, revision, useCount, builtIn FROM workouts WHERE id = 'w1'"
                ).use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Tabata Classic", rs.getString("name"))
                    assertEquals(1, rs.getInt("isFavorite"))
                    assertEquals(3, rs.getInt("revision"))
                    assertEquals(7, rs.getInt("useCount"))
                    assertEquals("built-in flag defaults to 0 for user workouts", 0, rs.getInt("builtIn"))
                }

                statement.executeQuery(
                    "SELECT workoutName, activeMillis, completionPercent, status, notes, lastHeartbeatAtMillis " +
                        "FROM sessions WHERE id = 's1'"
                ).use { rs ->
                    assertTrue(rs.next())
                    assertEquals("Tabata Classic", rs.getString("workoutName"))
                    assertEquals(240_000L, rs.getLong("activeMillis"))
                    assertEquals(100, rs.getInt("completionPercent"))
                    assertEquals("COMPLETED", rs.getString("status"))
                    assertEquals("felt good", rs.getString("notes"))
                    assertNull("new column starts NULL for pre-existing rows", rs.getObject("lastHeartbeatAtMillis"))
                }

                statement.executeQuery("SELECT COUNT(*) FROM session_events WHERE sessionId = 's1'").use { rs ->
                    rs.next()
                    assertEquals(2, rs.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM workout_tags").use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
                statement.executeQuery("SELECT COUNT(*) FROM folders").use { rs ->
                    rs.next()
                    assertEquals(1, rs.getInt(1))
                }
            }
        }
    }

    @Test
    fun `a starter workout keeps its built in flag across the migration`() {
        val tables = readSchema()
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createV1Database(tables)
            connection.createStatement().use { statement ->
                statement.execute(
                    "INSERT INTO workouts (id, name, description, type, planJson, iconKey, isFavorite, " +
                        "createdAt, updatedAt, revision, useCount) " +
                        "VALUES ('w2', 'HIIT Starter', '', 'HIIT', '{}', 'bolt', 0, 1, 1, 1, 0)"
                )
                PulseMigrations.ONE_TO_TWO.forEach(statement::execute)
                statement.execute("UPDATE workouts SET builtIn = 1 WHERE id = 'w2'")
                statement.executeQuery("SELECT name, builtIn FROM workouts WHERE id = 'w2'").use { rs ->
                    assertTrue(rs.next())
                    assertEquals("HIIT Starter", rs.getString("name"))
                    assertEquals(1, rs.getInt("builtIn"))
                }
            }
        }
    }
}
