package com.devi.overlay

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * The owner's chants, copied byte-for-byte into [dir].
 * Order lives in order.txt (one id per line); titles in titles.txt as "id<TAB>title".
 */
class ChantStore(private val dir: File) {
    private val orderFile = File(dir, "order.txt")
    private val titlesFile = File(dir, "titles.txt")

    @Synchronized fun ids(): List<String> =
        if (orderFile.exists()) orderFile.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()

    @Synchronized fun title(id: String): String = titles()[id] ?: id.substringBeforeLast('.')

    fun file(id: String): File = File(dir, id)

    @Synchronized fun add(input: InputStream, ext: String, title: String): String {
        dir.mkdirs()
        val id = "${UUID.randomUUID()}.$ext"
        val temp = File(dir, "$id.tmp")
        try {
            temp.outputStream().use { input.copyTo(it) }
            Files.move(temp.toPath(), file(id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            temp.delete()
            file(id).delete()
            throw e
        }
        writeAtomically(orderFile, (ids() + id).joinToString("\n"))
        writeTitles(titles() + (id to clean(title)))
        return id
    }

    @Synchronized fun remove(id: String) {
        val ids = ids()
        if (id !in ids) return
        writeAtomically(orderFile, (ids - id).joinToString("\n"))
        writeTitles(titles() - id)
        file(id).delete()
    }

    /** First run only: fills an empty store with the chants that ship inside the app. */
    @Synchronized fun seedOnce(bundled: List<Pair<String, String>>, open: (String) -> InputStream): Boolean {
        dir.mkdirs()
        val marker = File(dir, ".seeded")
        if (marker.exists() || ids().isNotEmpty()) { marker.createNewFile(); return false }
        val done = bundled.filter { (name, _) ->
            try { open(name).use { input -> file(name).outputStream().use { input.copyTo(it) } }; true }
            catch (e: Exception) { file(name).delete(); false }
        }
        writeAtomically(orderFile, done.joinToString("\n") { it.first })
        writeTitles(done.associate { it.first to clean(it.second) })
        marker.createNewFile()
        return done.isNotEmpty()
    }

    /** Adds chants that ship inside the app once per [tag], even on an install that was seeded long ago.
     *  Never repeats after the owner removes one. */
    @Synchronized fun addBundledOnce(tag: String, bundled: List<Pair<String, String>>): Boolean {
        dir.mkdirs()
        val marker = File(dir, ".bundled_$tag")
        if (marker.exists()) return false
        val have = ids()
        val fresh = bundled.filter { it.first !in have }
        writeAtomically(orderFile, (have + fresh.map { it.first }).joinToString("\n"))
        writeTitles(titles() + fresh.associate { it.first to clean(it.second) })
        marker.createNewFile()
        return fresh.isNotEmpty()
    }

    private fun clean(title: String) = title.replace('\t', ' ').replace('\n', ' ').trim().ifEmpty { "Chant" }

    private fun titles(): Map<String, String> =
        if (!titlesFile.exists()) emptyMap()
        else titlesFile.readLines(Charsets.UTF_8).mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

    private fun writeTitles(map: Map<String, String>) =
        writeAtomically(titlesFile, map.entries.joinToString("\n") { "${it.key}\t${it.value}" })

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text, Charsets.UTF_8)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val BUNDLED = "bundled_chants"
        private const val VIDEOS = "bundled_videos"
        const val ASSET = "asset/"

        /** A chant that plays straight from inside the app (not copied out, so it isn't stored twice). */
        fun isAsset(id: String) = id.startsWith(ASSET)
        fun assetPath(id: String) = id.removePrefix(ASSET)
        private val videoExts = setOf("mp4", "webm", "mkv", "mov", "m4v")

        /** A full YouTube video kept as a chant: plays as a video, or as sound with the screen off. */
        fun isVideo(id: String) = id.substringAfterLast('.', "").lowercase() in videoExts

        fun forContext(ctx: android.content.Context): ChantStore {
            val dir = File(ctx.filesDir, "chants").apply { mkdirs() }
            val store = ChantStore(dir)
            if (!File(dir, ".seeded").exists()) {
                store.seedOnce(readTitles(ctx, "$BUNDLED/titles.txt")) { ctx.assets.open("$BUNDLED/$it") }
            }
            if (!File(dir, ".bundled_$VIDEO_TAG").exists()) {
                store.addBundledOnce(VIDEO_TAG, readTitles(ctx, "$VIDEOS/titles.txt").map { (n, t) -> "$ASSET$VIDEOS/$n" to t })
            }
            return store
        }

        private const val VIDEO_TAG = "videos1"

        private fun readTitles(ctx: android.content.Context, path: String): List<Pair<String, String>> = try {
            ctx.assets.open(path).bufferedReader(Charsets.UTF_8).readLines().mapNotNull { line ->
                val i = line.indexOf('\t'); if (i <= 0) null else line.substring(0, i) to line.substring(i + 1).trim()
            }
        } catch (e: Exception) { emptyList() }
    }
}
