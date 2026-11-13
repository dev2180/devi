package com.devi.overlay

/** One full-screen page of the feed. */
sealed interface FeedItem {
    data class Picture(val id: String) : FeedItem
    data class Video(val chantId: String) : FeedItem
}

/** What the feed shows, in what order, and which chant a picture brings. Pure, so it is tested. */
object FeedOrder {
    /** Her main picture first, then pictures and videos woven together so neither runs on too long. */
    fun build(pictures: List<String>, main: String?, videos: List<String>): List<FeedItem> {
        val pics = listOfNotNull(main?.takeIf { it in pictures }) + pictures.filter { it != main }
        val out = mutableListOf<FeedItem>()
        for (i in 0 until maxOf(pics.size, videos.size)) {
            pics.getOrNull(i)?.let { out += FeedItem.Picture(it) }
            videos.getOrNull(i)?.let { out += FeedItem.Video(it) }
        }
        return out
    }

    /** A picture plays the chant paired with it, else her first sung chant. A picture never starts a video. */
    fun chantForPicture(paired: String?, chants: List<String>): String? =
        paired?.takeIf { it in chants && !ChantStore.isVideo(it) } ?: chants.firstOrNull { !ChantStore.isVideo(it) }

    /** The feed never ends: positions run over a long loop, starting in its middle on the first page. */
    const val LOOP = 100_000
    fun pageCount(size: Int) = if (size <= 1) size else LOOP
    fun startPosition(size: Int) = if (size <= 1) 0 else (LOOP / 2) - (LOOP / 2) % size
    fun indexOf(position: Int, size: Int) = if (size == 0) -1 else position % size
}
