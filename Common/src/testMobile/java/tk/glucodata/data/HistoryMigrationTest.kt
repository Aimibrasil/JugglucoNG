package tk.glucodata.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * The Room migration runner (plan task H3). Runs the production `Migration`
 * objects against the exported schemas in `Common/schemas/` and lets
 * `MigrationTestHelper` check the result matches the committed schema, so a
 * migration that drops a column or a table fails here.
 *
 * The per-version starting points are the released schemas recovered in H2:
 * v11 (1.1.2-Alpha) and v12 (1.1.3-Alpha). v32 is the current version.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// Robolectric installs Conscrypt as the top JCA provider for the whole test JVM;
// leave the platform provider in place so this class cannot change what the JCA
// tests that share the JVM (Ottai, iCan, Anytime, AiDex crypto/auth) exercise.
@ConscryptMode(ConscryptMode.Mode.OFF)
class HistoryMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HistoryDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private fun migrate(from: Int) {
        helper.createDatabase(DB_NAME, from).close()
        helper.runMigrationsAndValidate(DB_NAME, 32, true, *HistoryDatabase.ALL_MIGRATIONS).close()
    }

    @Test
    fun migratesFromTheReleasedV11SchemaToCurrent() {
        migrate(from = 11)
    }

    @Test
    fun migratesFromTheReleasedV12SchemaToCurrent() {
        migrate(from = 12)
    }

    private companion object {
        const val DB_NAME = "history-migration-test.db"
    }
}
