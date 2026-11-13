package com.devi.overlay

/** Finds a YouTube video in pasted or shared text and gives back its plain watch link. */
object YouTubeLink {
    private val patterns = listOf(
        Regex("""(?:https?://)?(?:www\.|m\.|music\.)?youtube\.com/watch\?\S*?\bv=([A-Za-z0-9_-]{11})"""),
        Regex("""(?:https?://)?youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""(?:https?://)?(?:www\.|m\.)?youtube\.com/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})"""),
    )

    fun find(text: String): String? {
        for (p in patterns) {
            val id = p.find(text)?.groupValues?.get(1) ?: continue
            return "https://www.youtube.com/watch?v=$id"
        }
        return null
    }
}
