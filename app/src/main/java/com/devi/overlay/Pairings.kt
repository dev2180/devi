package com.devi.overlay

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Which chant plays when a given picture opens. Stored as "imageId=chantId" lines in pairs.txt. */
class Pairings(private val dir: File) {
    private val file = File(dir, "pairs.txt")

    @Synchronized fun chantFor(imageId: String): String? = read()[imageId]

    @Synchronized fun pair(imageId: String, chantId: String?) {
        val map = read().toMutableMap()
        if (chantId == null) map.remove(imageId) else map[imageId] = chantId
        write(map)
    }

    @Synchronized fun forgetChant(chantId: String) = write(read().filterValues { it != chantId })

    @Synchronized fun forgetImage(imageId: String) = write(read() - imageId)

    private fun read(): Map<String, String> =
        if (!file.exists()) emptyMap()
        else file.readLines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i) to line.substring(i + 1)
        }.toMap()

    private fun write(map: Map<String, String>) {
        dir.mkdirs()
        val temp = File(dir, "pairs.txt.tmp")
        temp.writeText(map.entries.joinToString("\n") { "${it.key}=${it.value}" })
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        fun forContext(ctx: android.content.Context) = Pairings(File(ctx.filesDir, "chants").apply { mkdirs() })
    }
}
