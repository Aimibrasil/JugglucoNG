package tk.glucodata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The string values that now travel with the mirrored display preferences:
 * the sensor selection (primary first) and the pinned sensor colours. The
 * payload is one key per line, so a value with a line break in it — which is
 * exactly how the colour overrides are stored — must survive the trip as one
 * key, not be read back as several.
 */
class WearPrefsSyncCodecTests {

    @Test
    fun selectionKeepsThePhonesOrder() {
        val wire = WearPrefsSync.selectionToWire(listOf("X-222227KT3T", "P225043JMV"))
        assertFalse(wire.contains('\n'))
        assertEquals(listOf("X-222227KT3T", "P225043JMV"), WearPrefsSync.selectionFromWire(wire))
    }

    @Test
    fun selectionDropsBlanksAndSurroundingSpace() {
        assertEquals(
            listOf("A", "B"),
            WearPrefsSync.selectionFromWire(" A ,, B ,"),
        )
        assertEquals("", WearPrefsSync.selectionToWire(listOf("", "  ")))
        assertEquals(emptyList<String>(), WearPrefsSync.selectionFromWire(""))
    }

    @Test
    fun multiLineValuesTravelOnOneLine() {
        val stored = "X-222227KT3T|4283215696\nP225043JMV|4294198070"
        val line = WearPrefsSync.escapeLine(stored)
        assertFalse(line.contains('\n'))
        assertEquals(stored, WearPrefsSync.unescapeLine(line))
    }

    @Test
    fun backslashesSurviveTheEscaping() {
        listOf("a\\b", "a\\nb", "\\", "\\\\n", "plain", "").forEach { value ->
            assertEquals(value, WearPrefsSync.unescapeLine(WearPrefsSync.escapeLine(value)))
        }
    }
}
