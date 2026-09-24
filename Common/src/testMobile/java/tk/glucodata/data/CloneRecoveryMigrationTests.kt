package tk.glucodata.data

import android.database.SQLException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * Clone recovery migrations on the H3 runner (plan task H4). Replaces the JDBC
 * harness that read Room's generated code from `build/generated/ksp`; the
 * production `Migration` objects and the committed schemas are now the only
 * inputs, and `runMigrationsAndValidate` checks the resulting shape.
 *
 * The clone recovery artifacts (`recoveryId` backfill, `clone_journal_recovery_tombstones`,
 * `clone_recovery_imports`) are all created by the step that calls
 * `ensureCloneSchema` (v31 -> v32 here), so a released v11 history exercises them
 * end to end.
 *
 * Not covered: the old harness injected a failing statement mid-migration to
 * check the transaction rollback. `MigrationTestHelper` runs the real chain and
 * cannot inject a failure inside a migration, so that case is dropped rather
 * than faked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Keep Conscrypt from becoming the JVM-wide top JCA provider; see HistoryMigrationTest.
@ConscryptMode(ConscryptMode.Mode.OFF)
class CloneRecoveryMigrationTests {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistoryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun seedReleasedV11(seed: SupportSQLiteDatabase.() -> Unit) {
        helper.createDatabase(DB_NAME, 11).use { it.seed() }
    }

    private fun migrateToCurrent(): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(DB_NAME, HISTORY_DATABASE_VERSION, true, *HistoryDatabase.ALL_MIGRATIONS)

    @Test
    fun recoverySchemaIsCreatedAndRowsSurviveFromV11() {
        seedReleasedV11 {
            execSQL(
                "INSERT INTO journal_entries " +
                    "(id, timestamp, entryType, title, source, createdAt, updatedAt, nsUploadedAt) " +
                    "VALUES (7, 1000, 'note', 'Keep this note', 'manual', 900, 1100, 1200)"
            )
            execSQL(
                "INSERT INTO history_readings (id, timestamp, sensorSerial, value, rawValue) " +
                    "VALUES (9, 1000, 'SENSOR', 120.0, 119.0)"
            )
        }

        migrateToCurrent().use { db ->
            db.query("SELECT title, nsUploadedAt FROM journal_entries WHERE id = 7").use { cursor ->
                assertTrue("the journal row survived", cursor.moveToFirst())
                assertEquals("Keep this note", cursor.getString(0))
                assertEquals(1200L, cursor.getLong(1))
            }
            db.query("SELECT value FROM history_readings WHERE id = 9").use { cursor ->
                assertTrue("the reading survived", cursor.moveToFirst())
                assertEquals(120.0, cursor.getDouble(0), 0.001)
            }
            db.query("SELECT recoveryId FROM journal_entries WHERE id = 7").use { cursor ->
                assertTrue("the recovery identity was assigned", cursor.moveToFirst())
                assertTrue(
                    "recoveryId is a 32-hex id",
                    cursor.getString(0).matches(Regex("[0-9a-f]{32}"))
                )
            }
            // Both recovery tables are Room entities, so validateDroppedTables already
            // checked their shape; this only proves they exist and are queryable.
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' " +
                    "AND name IN ('clone_journal_recovery_tombstones', 'clone_recovery_imports')"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("both recovery tables exist", 2, cursor.getInt(0))
            }
        }
    }

    @Test
    fun duplicateJournalRecoveryIdentityIsRejected() {
        seedReleasedV11 {
            execSQL(
                "INSERT INTO journal_entries " +
                    "(id, timestamp, entryType, title, source, createdAt, updatedAt) " +
                    "VALUES (7, 1000, 'note', 'A', 'manual', 900, 1100)"
            )
            execSQL(
                "INSERT INTO journal_entries " +
                    "(id, timestamp, entryType, title, source, createdAt, updatedAt) " +
                    "VALUES (8, 2000, 'note', 'B', 'manual', 1900, 2100)"
            )
        }

        migrateToCurrent().use { db ->
            try {
                db.execSQL(
                    "UPDATE journal_entries SET recoveryId = " +
                        "(SELECT recoveryId FROM journal_entries WHERE id = 7) WHERE id = 8"
                )
                fail("Duplicate journal recovery identity accepted")
            } catch (expected: SQLException) {
                assertTrue(expected.message.orEmpty().contains("UNIQUE"))
            }
            db.query("SELECT COUNT(DISTINCT recoveryId) FROM journal_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("each row got its own identity", 2, cursor.getInt(0))
            }
        }
    }

    @Test
    fun recoveryTombstoneRejectsDuplicateRecoveryIdentity() {
        seedReleasedV11 { }

        migrateToCurrent().use { db ->
            db.execSQL(
                "INSERT INTO clone_journal_recovery_tombstones " +
                    "(stableBaseId, recoveryId, deletedAt) " +
                    "VALUES ('a', '0123456789abcdef0123456789abcdef', 700)"
            )
            try {
                db.execSQL(
                    "INSERT INTO clone_journal_recovery_tombstones " +
                        "(stableBaseId, recoveryId, deletedAt) " +
                        "VALUES ('b', '0123456789abcdef0123456789abcdef', 800)"
                )
                fail("Duplicate recovery tombstone identity accepted")
            } catch (expected: SQLException) {
                assertTrue(expected.message.orEmpty().contains("UNIQUE"))
            }
            db.query("SELECT COUNT(*) FROM clone_journal_recovery_tombstones").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun receiptTableRejectsDuplicateJobIds() {
        seedReleasedV11 { }

        migrateToCurrent().use { db ->
            db.execSQL("INSERT INTO clone_recovery_imports (jobId, sha256) VALUES ('job', 'first')")
            try {
                db.execSQL("INSERT INTO clone_recovery_imports (jobId, sha256) VALUES ('job', 'second')")
                fail("Duplicate receipt accepted")
            } catch (expected: SQLException) {
                assertTrue(expected.message.orEmpty().contains("UNIQUE"))
            }
            db.query("SELECT sha256 FROM clone_recovery_imports").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("first", cursor.getString(0))
                assertEquals(1, cursor.count)
            }
        }
    }

    @Test
    fun migrationDoesNotChangeSeededHistory() {
        seedReleasedV11 {
            execSQL(
                "INSERT INTO history_readings (id, timestamp, sensorSerial, value, rawValue) " +
                    "VALUES (9, 1000, 'SENSOR', 120.0, 119.0)"
            )
        }

        migrateToCurrent().use { db ->
            db.query("SELECT value, rawValue FROM history_readings WHERE id = 9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(120.0, cursor.getDouble(0), 0.001)
                assertEquals(119.0, cursor.getDouble(1), 0.001)
            }
        }
    }

    private companion object {
        const val DB_NAME = "clone-recovery-migration-test.db"
    }
}
