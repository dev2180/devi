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

class ImageStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var dir: File

    @Before fun setUp() { dir = tmp.newFolder("images") }

    private fun store() = ImageStore(dir)
    private fun bytes(vararg b: Int): InputStream = ByteArrayInputStream(ByteArray(b.size) { b[it].toByte() })
    private fun throwingStream(): InputStream = object : InputStream() {
        private var n = 0
        override fun read(): Int = if (n++ < 10) 1 else throw IOException("disk gone")
    }

    @Test fun emptyStoreHasNoMain() {
        val s = store()
        assertNull(s.main()); assertEquals(emptyList<String>(), s.ids()); assertEquals(0, s.mainIndex())
    }

    @Test fun firstAddedBecomesMain() {
        val s = store()
        val a = s.add(bytes(1, 2, 3), "jpg"); s.add(bytes(4), "png")
        assertEquals(a, s.main())
    }

    @Test fun bytesCopiedExactly() {
        val s = store()
        val a = s.add(bytes(9, 8, 7, 6), "webp")
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), s.file(a).readBytes())
    }

    @Test fun setMainAndIndex() {
        val s = store()
        s.add(bytes(1), "jpg"); val b = s.add(bytes(2), "jpg"); s.setMain(b)
        assertEquals(b, s.main()); assertEquals(1, s.mainIndex())
    }

    @Test fun setMainIgnoresUnknownId() {
        val s = store()
        val a = s.add(bytes(1), "jpg"); s.setMain("nope.jpg")
        assertEquals(a, s.main())
    }

    @Test fun removingMainPromotesFirstRemaining() {
        val s = store()
        val a = s.add(bytes(1), "jpg"); val b = s.add(bytes(2), "jpg"); s.remove(a)
        assertEquals(b, s.main()); assertFalse(s.file(a).exists())
    }

    @Test fun removingLastClearsMain() {
        val s = store()
        val a = s.add(bytes(1), "jpg"); s.remove(a)
        assertNull(s.main()); assertTrue(s.ids().isEmpty())
    }

    @Test fun failedCopyLeavesNothing() {
        val s = store()
        assertThrows(IOException::class.java) { s.add(throwingStream(), "jpg") }
        assertTrue(s.ids().isEmpty())
        assertTrue(dir.listFiles()!!.none { it.extension == "jpg" || it.extension == "tmp" })
    }

    @Test fun persistsAcrossInstances() {
        val s = store()
        val a = s.add(bytes(1), "jpg")
        assertEquals(listOf(a), ImageStore(dir).ids()); assertEquals(a, ImageStore(dir).main())
    }

    @Test fun removeWorksWhenMainFileMissing() {
        val s = store()
        val a = s.add(bytes(1), "jpg"); val b = s.add(bytes(2), "jpg")
        File(dir, "main.txt").delete() // e.g. process died between writes
        s.remove(b)
        assertEquals(listOf(a), s.ids()); assertEquals(a, s.main())
    }
}
