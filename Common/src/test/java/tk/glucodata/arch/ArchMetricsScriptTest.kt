package tk.glucodata.arch

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs `scripts/arch-metrics.sh` against a small fixture tree and checks the
 * numbers it reports (plan task T0.3). The script is greps; this is what keeps a
 * broken grep from silently reporting "0, all clean".
 *
 * The fixture is written line by line so each metric has exactly one known
 * source, and the assertions are the contract of the metric definitions.
 */
class ArchMetricsScriptTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "scripts/arch-metrics.sh").isFile }
        ?: error("repository root with scripts/arch-metrics.sh not found")

    private val script = File(repoRoot, "scripts/arch-metrics.sh")
    private lateinit var fixture: File

    @After
    fun cleanUp() {
        if (::fixture.isInitialized && fixture.exists()) fixture.deleteRecursively()
    }

    private fun file(relative: String, content: String) {
        val target = File(fixture, relative)
        target.parentFile.mkdirs()
        target.writeText(content)
    }

    private fun writeFixture() {
        fixture = Files.createTempDirectory("arch-metrics-fixture").toFile()

        // Directly in the root package: touches Applic. and Natives., and carries
        // one of each raw-concurrency / swallowed-error pattern.
        file(
            "Common/src/main/java/tk/glucodata/A.java",
            """
            package tk.glucodata;
            class A {
                @Volatile int v;
                void f() {
                    String s = Applic.x;
                    Natives.used();
                    new Thread();
                    new Handler();
                    CoroutineScope();
                    runCatching { g() }.getOrNull();
                    String PREFS = "aprefs";
                    try { g(); } catch (Throwable t) { }
                }
                void g() { }
            }
            """.trimIndent(),
        )

        // A Composable that reaches into JNI, and a Kotlin object singleton.
        file(
            "Common/src/main/java/tk/glucodata/B.kt",
            """
            package tk.glucodata
            @Composable fun b() { Natives.used() }
            object B
            """.trimIndent(),
        )

        // Two native declarations, only one of which is ever called.
        file(
            "Common/src/main/java/tk/glucodata/Natives.java",
            """
            package tk.glucodata;
            class Natives { public static native void used(); public static native void deadOne(); }
            """.trimIndent(),
        )

        // A subpackage file: still counts as touching Applic., but not as a root
        // package file.
        file(
            "Common/src/main/java/tk/glucodata/sub/C.java",
            """
            package tk.glucodata.sub;
            class C { void f() { String s = Applic.x; } }
            """.trimIndent(),
        )

        // Same FQN in both flavour source sets: one duplicate pair.
        file("Common/src/mobile/java/tk/glucodata/Dup.java", "package tk.glucodata;\nclass Dup {}\n")
        file("Common/src/wear/java/tk/glucodata/Dup.java", "package tk.glucodata;\nclass Dup {}\n")
    }

    private fun metrics(): JSONObject {
        writeFixture()
        val process = ProcessBuilder("bash", script.absolutePath, "--print", "--root", fixture.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        assertEquals("arch-metrics.sh failed:\n$output", 0, exit)
        return JSONObject(output)
    }

    @Test
    fun countsStaticCouplingAcrossTheWholeSourceTree() {
        val metrics = metrics()
        assertEquals(2, metrics.getInt("files_touching_Applic_static"))
        assertEquals(2, metrics.getInt("files_touching_Natives_static"))
    }

    @Test
    fun countsTheTwinsAcrossMobileAndWear() {
        assertEquals(1, metrics().getInt("duplicate_fqn_pairs"))
    }

    @Test
    fun countsComposablesThatReachIntoJni() {
        assertEquals(1, metrics().getInt("composables_calling_jni"))
    }

    @Test
    fun countsConcurrencyAndErrorPatterns() {
        val metrics = metrics()
        assertEquals(1, metrics.getInt("volatile_fields"))
        assertEquals(1, metrics.getInt("ad_hoc_coroutine_scopes"))
        assertEquals(1, metrics.getInt("raw_threads"))
        assertEquals(1, metrics.getInt("raw_handlers"))
        assertEquals(1, metrics.getInt("catch_throwable"))
        assertEquals(1, metrics.getInt("runcatching_swallowed"))
        assertEquals(1, metrics.getInt("shared_prefs_files"))
    }

    @Test
    fun countsObjectSingletonsAndRootPackageSize() {
        val metrics = metrics()
        assertEquals(1, metrics.getInt("kotlin_object_singletons"))
        // A.java, B.kt, Natives.java — not the subpackage file.
        assertEquals(3, metrics.getInt("root_package_files"))
        assertTrue("root_package_loc should count lines", metrics.getInt("root_package_loc") > 0)
    }

    @Test
    fun countsNativeDeclarationsNothingCalls() {
        assertEquals(1, metrics().getInt("dead_native_declarations"))
    }
}
