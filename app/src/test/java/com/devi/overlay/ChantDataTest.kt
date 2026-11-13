package com.devi.overlay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class FadeCurveTest {
    @Test fun fadeInStartsAtZeroEndsAtOne() {
        assertEquals(0f, FadeCurve.fadeIn(0), 0.001f)
        assertEquals(1f, FadeCurve.fadeIn(10_000), 0.001f)
        assertEquals(0.707f, FadeCurve.fadeIn(5_000), 0.01f)
    }

    @Test fun fadeInClamps() {
        assertEquals(0f, FadeCurve.fadeIn(-5), 0.001f)
        assertEquals(1f, FadeCurve.fadeIn(20_000), 0.001f)
    }

    @Test fun fadeOutFromCurrentGain() {
        assertEquals(0.4f, FadeCurve.fadeOut(0, 0.4f), 0.001f)
        assertEquals(0f, FadeCurve.fadeOut(1_500, 0.4f), 0.001f)
        assertTrue(FadeCurve.fadeOut(750, 0.4f) < 0.4f)
    }
}

class ChantStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dir: File

    @Before fun setUp() { dir = tmp.newFolder("chants") }

    private fun bytes(vararg b: Int): InputStream = ByteArrayInputStream(ByteArray(b.size) { b[it].toByte() })

    @Test fun addKeepsTitleAndBytes() {
        val s = ChantStore(dir)
        val id = s.add(bytes(5, 6, 7), "m4a", "Ae Devi Bhairavi")
        assertEquals("Ae Devi Bhairavi", s.title(id))
        assertArrayEquals(byteArrayOf(5, 6, 7), s.file(id).readBytes())
        assertEquals(listOf(id), s.ids())
    }

    @Test fun removeDeletesFile() {
        val s = ChantStore(dir)
        val id = s.add(bytes(1), "mp3", "A")
        s.remove(id)
        assertTrue(s.ids().isEmpty()); assertFalse(s.file(id).exists())
    }

    @Test fun failedCopyLeavesNothing() {
        val s = ChantStore(dir)
        val bad = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 5) 1 else throw IOException("gone")
        }
        assertThrows(IOException::class.java) { s.add(bad, "mp3", "Broken") }
        assertTrue(s.ids().isEmpty())
        assertTrue(dir.listFiles()!!.none { it.extension == "mp3" || it.extension == "tmp" })
    }

    @Test fun persistsAcrossInstances() {
        val id = ChantStore(dir).add(bytes(1), "ogg", "Stuti, with comma")
        assertEquals(listOf(id), ChantStore(dir).ids())
        assertEquals("Stuti, with comma", ChantStore(dir).title(id))
    }
}

class PairingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun pairAndLookup() {
        val p = Pairings(tmp.root)
        p.pair("img1", "ch1")
        assertEquals("ch1", Pairings(tmp.root).chantFor("img1"))
    }

    @Test fun pairNullUnpairs() {
        val p = Pairings(tmp.root)
        p.pair("img1", "ch1"); p.pair("img1", null)
        assertNull(p.chantFor("img1"))
    }

    @Test fun removingChantClearsPairs() {
        val p = Pairings(tmp.root)
        p.pair("img1", "ch1"); p.pair("img2", "ch1"); p.pair("img3", "ch2")
        p.forgetChant("ch1")
        assertNull(p.chantFor("img1")); assertNull(p.chantFor("img2")); assertEquals("ch2", p.chantFor("img3"))
    }

    @Test fun removingImageClearsPair() {
        val p = Pairings(tmp.root)
        p.pair("img1", "ch1")
        p.forgetImage("img1")
        assertNull(p.chantFor("img1"))
    }
}
