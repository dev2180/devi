package com.devi.overlay

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * The owner's pictures, copied byte-for-byte into [dir].
 * Order lives in order.txt (one id per line), the main picture's id in main.txt.
 */
class ImageStore(private val dir: File) {
    private val orderFile = File(dir, "order.txt")
    private val mainFile = File(dir, "main.txt")

    @Synchronized fun ids(): List<String> =
        if (orderFile.exists()) orderFile.readLines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()

    @Synchronized fun main(): String? {
        val ids = ids()
        val saved = if (mainFile.exists()) mainFile.readText().trim() else ""
        return saved.takeIf { it in ids } ?: ids.firstOrNull()
    }

    @Synchronized fun mainIndex(): Int = main()?.let { ids().indexOf(it) }?.coerceAtLeast(0) ?: 0

    fun file(id: String): File = File(dir, id)

    /** Copies [input] unchanged. On failure nothing is left behind and the exception is rethrown. */
    @Synchronized fun add(input: InputStream, extension: String): String {
        dir.mkdirs()
        val id = "${UUID.randomUUID()}.$extension"
        val temp = File(dir, "$id.tmp")
        try {
            temp.outputStream().use { out -> input.copyTo(out) }
            Files.move(temp.toPath(), file(id).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            temp.delete()
            file(id).delete()
            throw e
        }
        val ids = ids() + id
        writeAtomically(orderFile, ids.joinToString("\n"))
        if (!mainFile.exists() || mainFile.readText().trim() !in ids) writeAtomically(mainFile, id)
        return id
    }

    @Synchronized fun remove(id: String) {
        val ids = ids()
        if (id !in ids) return
        val remaining = ids - id
        writeAtomically(orderFile, remaining.joinToString("\n"))
        val savedMain = if (mainFile.exists()) mainFile.readText().trim() else null
        if (savedMain == null || savedMain !in remaining) {
            val next = remaining.firstOrNull()
            if (next != null) writeAtomically(mainFile, next) else mainFile.delete()
        }
        file(id).delete()
    }

    /**
     * The first time the app runs, fills an empty store with the pictures that ship inside it.
     * Never again after that (removing them all keeps them removed), never over the owner's own.
     */
    @Synchronized fun seedOnce(names: List<String>, main: String?, open: (String) -> InputStream): Boolean {
        dir.mkdirs()
        val marker = File(dir, ".seeded")
        if (marker.exists() || ids().isNotEmpty()) { marker.createNewFile(); return false }
        val done = names.filter { name ->
            try { open(name).use { input -> file(name).outputStream().use { input.copyTo(it) } }; true }
            catch (e: Exception) { file(name).delete(); false }
        }
        writeAtomically(orderFile, done.joinToString("\n"))
        (main?.takeIf { it in done } ?: done.firstOrNull())?.let { writeAtomically(mainFile, it) }
        marker.createNewFile()
        return done.isNotEmpty()
    }

    @Synchronized fun setMain(id: String) {
        if (id in ids()) writeAtomically(mainFile, id)
    }

    private fun writeAtomically(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private const val BUNDLED = "bundled"

        fun forContext(ctx: android.content.Context): ImageStore {
            val dir = File(ctx.filesDir, "images").apply { mkdirs() }
            val store = ImageStore(dir)
            if (File(dir, ".seeded").exists()) return store
            val assets = ctx.assets
            val order = try { assets.open("$BUNDLED/order.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() } } catch (e: Exception) { emptyList() }
            val main = try { assets.open("$BUNDLED/main.txt").bufferedReader().readText().trim() } catch (e: Exception) { null }
            store.seedOnce(order, main) { assets.open("$BUNDLED/$it") }
            return store
        }
    }
}
