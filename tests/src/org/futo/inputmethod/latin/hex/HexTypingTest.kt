package org.futo.inputmethod.latin.hex

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HexTypingTest {
    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    @Test
    fun packagedEnglishModelUsesPreviousWordAndTapPositions() {
        val decoder = SpatialDecoder(TypingModel.asset(assets, "en"))
        val grid = qwertyGrid()
        val t = grid.centres.getValue('t')
        val k = grid.centres.getValue('k')
        val o = grid.centres.getValue('o')
        val mistap = KeyPoint(k.x * 0.6 + o.x * 0.4, k.y * 0.6 + o.y * 0.4)

        fun replacement(secondTap: KeyPoint) = TypingWord(
            decoder, "tk", listOf(
                decoder.score(grid, t.x, t.y),
                decoder.score(grid, secondTap.x, secondTap.y),
            ), "need", suggestSwearWords = false
        ).replacement()

        assertEquals("to", replacement(mistap))
        assertNotEquals("to", replacement(k))
        assertNull(word(decoder, grid, "tbh", "").replacement())
    }

    @Test
    fun packagedGermanModelRepairsTranspositionOnQwerty() {
        val decoder = SpatialDecoder(TypingModel.asset(assets, "de"))
        assertEquals("dank", word(decoder, qwertyGrid(), "dnak", "vielen").replacement())
    }

    private fun qwertyGrid(): KeyGrid {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val offsets = listOf(0.0, 0.25, 0.75)
        val centres = buildMap {
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, letter ->
                    put(letter, KeyPoint((x + offsets[y]) * 100, y * 130.0))
                }
            }
        }
        return KeyGrid(centres, 100.0, 130.0)
    }

    private fun word(decoder: SpatialDecoder, grid: KeyGrid, text: String, previous: String): TypingWord {
        val taps = text.map { letter ->
            val point = grid.centres.getValue(letter)
            decoder.score(grid, point.x, point.y)
        }
        return TypingWord(decoder, text, taps, previous, suggestSwearWords = false)
    }
}
