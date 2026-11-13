package com.devi.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class YouTubeLinkTest {
    private fun id(text: String) = YouTubeLink.find(text)

    @Test fun watchLink() = assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
    @Test fun shortLink() = assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("https://youtu.be/dQw4w9WgXcQ?si=abc"))
    @Test fun shorts() = assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("youtube.com/shorts/dQw4w9WgXcQ"))
    @Test fun mobileAndMusic() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD"))
    }
    @Test fun liveAndEmbed() {
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("https://www.youtube.com/embed/dQw4w9WgXcQ"))
    }
    @Test fun sharedTextAroundIt() =
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", id("Linga Bhairavi Stuti\nhttps://youtu.be/dQw4w9WgXcQ shared via YouTube"))
    @Test fun notYouTube() {
        assertNull(id("https://vimeo.com/22439234"))
        assertNull(id("hello"))
        assertNull(id("https://www.youtube.com/@sadhguru"))
    }
}

class ChantSeedTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun seedsOnceWithTitles() {
        val dir = tmp.newFolder("chants")
        val s = ChantStore(dir)
        val bundled = listOf("c1.m4a" to "Linga Bhairavi Stuti", "c2.m4a" to "Devi Smaranam")
        assertTrue(s.seedOnce(bundled) { ByteArrayInputStream(byteArrayOf(1, 2)) })
        assertEquals(listOf("c1.m4a", "c2.m4a"), s.ids())
        assertEquals("Devi Smaranam", s.title("c2.m4a"))
        s.remove("c1.m4a")
        assertEquals(false, ChantStore(dir).seedOnce(bundled) { ByteArrayInputStream(byteArrayOf(1)) })
        assertEquals(listOf("c2.m4a"), s.ids())
    }

    @Test fun neverOverOwnersChants() {
        val s = ChantStore(tmp.newFolder("c"))
        s.add(ByteArrayInputStream(byteArrayOf(9)), "mp3", "Mine")
        assertEquals(false, s.seedOnce(listOf("c1.m4a" to "X")) { ByteArrayInputStream(byteArrayOf(1)) })
        assertEquals(1, s.ids().size)
    }
}

class VideoChantTest {
    @Test fun videosAreRecognised() {
        assertTrue(ChantStore.isVideo("abc.mp4"))
        assertTrue(ChantStore.isVideo("abc.WEBM"))
        assertEquals(false, ChantStore.isVideo("chant1.m4a"))
        assertEquals(false, ChantStore.isVideo("x.opus"))
    }

    @Test fun videoStartsAtFullVolumeOnlyWhenChosen() {
        assertTrue(ChantPlayback.instant(isVideo = true, byViewer = false))
        assertEquals(false, ChantPlayback.instant(isVideo = true, byViewer = true))
        assertEquals(false, ChantPlayback.instant(isVideo = false, byViewer = false))
    }

    @Test fun videosPlayOnceChantsLoop() {
        assertEquals(false, ChantPlayback.loops(isVideo = true))
        assertTrue(ChantPlayback.loops(isVideo = false))
    }
}
