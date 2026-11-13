package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ImageSeedTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bundled = linkedMapOf("b.jpg" to byteArrayOf(2), "a.jpg" to byteArrayOf(1), "c.jpg" to byteArrayOf(3))
    private fun seed(s: ImageStore) =
        s.seedOnce(listOf("a.jpg", "b.jpg", "c.jpg"), main = "b.jpg") { ByteArrayInputStream(bundled.getValue(it)) }

    @Test fun seedsInOrderWithMain() {
        val s = ImageStore(tmp.newFolder("images"))
        assertTrue(seed(s))
        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), s.ids())
        assertEquals("b.jpg", s.main())
        assertEquals(3, s.file("c.jpg").readBytes()[0].toInt())
    }

    @Test fun seedsOnlyOnceEvenAfterRemovingAll() {
        val dir = tmp.newFolder("images")
        val s = ImageStore(dir)
        seed(s)
        s.ids().forEach { s.remove(it) }
        assertFalse(seed(ImageStore(dir)))
        assertEquals(emptyList<String>(), s.ids())
    }

    @Test fun neverSeedsOverExistingPictures() {
        val s = ImageStore(tmp.newFolder("images"))
        val mine = s.add(ByteArrayInputStream(byteArrayOf(9)), "png")
        assertFalse(seed(s))
        assertEquals(listOf(mine), s.ids())
    }

    @Test fun missingMainFallsBackToFirst() {
        val s = ImageStore(File(tmp.root, "x").apply { mkdirs() })
        s.seedOnce(listOf("a.jpg"), main = "nope.jpg") { ByteArrayInputStream(byteArrayOf(1)) }
        assertEquals("a.jpg", s.main())
    }
}
