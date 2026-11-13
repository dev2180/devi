package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FeedOrderTest {
    private fun p(id: String) = FeedItem.Picture(id)
    private fun v(id: String) = FeedItem.Video(id)

    @Test fun mainFirstThenWoven() = assertEquals(
        listOf(p("b"), v("x.mp4"), p("a"), v("y.webm"), p("c")),
        FeedOrder.build(listOf("a", "b", "c"), "b", listOf("x.mp4", "y.webm")),
    )

    @Test fun moreVideosThanPictures() = assertEquals(
        listOf(p("a"), v("1.mp4"), v("2.mp4")),
        FeedOrder.build(listOf("a"), null, listOf("1.mp4", "2.mp4")),
    )

    @Test fun emptyIsEmpty() = assertTrue(FeedOrder.build(emptyList(), null, emptyList()).isEmpty())

    @Test fun pictureChant() {
        val chants = listOf("asset/bundled_videos/a.webm", "c1.m4a", "c2.m4a")
        assertEquals("c2.m4a", FeedOrder.chantForPicture("c2.m4a", chants))
        assertEquals("c1.m4a", FeedOrder.chantForPicture(null, chants))
        assertEquals("c1.m4a", FeedOrder.chantForPicture("asset/bundled_videos/a.webm", chants))
        assertEquals("c1.m4a", FeedOrder.chantForPicture("gone.m4a", chants))
        assertNull(FeedOrder.chantForPicture(null, listOf("v.mp4")))
    }

    @Test fun endlessLoopStartsOnFirstPage() {
        val size = 13
        val start = FeedOrder.startPosition(size)
        assertEquals(0, FeedOrder.indexOf(start, size))
        assertEquals(12, FeedOrder.indexOf(start - 1, size))
        assertEquals(1, FeedOrder.pageCount(1))
        assertEquals(0, FeedOrder.startPosition(1))
    }
}

class BundledVideosTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun addedOnceToAnOldInstallAndRespectsRemoval() {
        val dir = tmp.newFolder("chants")
        val s = ChantStore(dir)
        s.seedOnce(listOf("c1.m4a" to "Stuti")) { java.io.ByteArrayInputStream(byteArrayOf(1)) }
        val vids = listOf("asset/bundled_videos/a.webm" to "Stuti", "asset/bundled_videos/b.mp4" to "Arati")
        assertTrue(s.addBundledOnce("videos1", vids))
        assertEquals(listOf("c1.m4a", "asset/bundled_videos/a.webm", "asset/bundled_videos/b.mp4"), s.ids())
        assertEquals("Arati", s.title("asset/bundled_videos/b.mp4"))
        s.remove("asset/bundled_videos/a.webm")
        assertEquals(false, ChantStore(dir).addBundledOnce("videos1", vids))
        assertEquals(listOf("c1.m4a", "asset/bundled_videos/b.mp4"), s.ids())
    }

    @Test fun assetIdsAreVideos() {
        assertTrue(ChantStore.isAsset("asset/bundled_videos/a.webm"))
        assertTrue(ChantStore.isVideo("asset/bundled_videos/a.webm"))
        assertEquals("bundled_videos/a.webm", ChantStore.assetPath("asset/bundled_videos/a.webm"))
        assertEquals(false, ChantStore.isAsset("c1.m4a"))
    }
}
