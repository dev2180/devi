package com.devi.overlay

enum class VolumeKey { UP, DOWN }

/**
 * Detects Volume Up -> Down -> Up, each key-down within [maxGapMs] of the previous one.
 * Key repeats (holding a button) are ignored. After firing, a fresh sequence is required.
 */
/**
 * Detects a volume-key [sequence] (default Up, Down, Up), each key-down within [maxGapMs] of the
 * previous one. Matches against the most recent presses, so overlapping attempts still count.
 * Key repeats (holding a button) are ignored. After firing, a fresh sequence is required.
 */
class ComboDetector(
    private val sequence: List<VolumeKey> = ComboPreset.UP_DOWN_UP.keys,
    private val maxGapMs: Long = 600,
) {
    private val recent = ArrayDeque<VolumeKey>()
    private var lastTimeMs = 0L

    fun onKeyDown(key: VolumeKey, timeMs: Long, isRepeat: Boolean): Boolean {
        if (isRepeat) return false
        if (recent.isNotEmpty() && timeMs - lastTimeMs > maxGapMs) recent.clear()
        recent.addLast(key)
        lastTimeMs = timeMs
        while (recent.size > sequence.size) recent.removeFirst()

        if (recent.toList() == sequence) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        recent.clear()
    }
}

enum class ComboPreset(val keys: List<VolumeKey>, val label: String) {
    UP_DOWN_UP(listOf(VolumeKey.UP, VolumeKey.DOWN, VolumeKey.UP), "Up, Down, Up"),
    DOWN_UP_DOWN(listOf(VolumeKey.DOWN, VolumeKey.UP, VolumeKey.DOWN), "Down, Up, Down"),
    UP_UP_DOWN(listOf(VolumeKey.UP, VolumeKey.UP, VolumeKey.DOWN), "Up, Up, Down"),
    DOWN_DOWN_UP(listOf(VolumeKey.DOWN, VolumeKey.DOWN, VolumeKey.UP), "Down, Down, Up"),
    UP_DOWN_UP_DOWN(listOf(VolumeKey.UP, VolumeKey.DOWN, VolumeKey.UP, VolumeKey.DOWN), "Up, Down, Up, Down"),
    UP_UP_UP(listOf(VolumeKey.UP, VolumeKey.UP, VolumeKey.UP), "Up, Up, Up"),
    DOWN_DOWN_DOWN(listOf(VolumeKey.DOWN, VolumeKey.DOWN, VolumeKey.DOWN), "Down, Down, Down"),
    UP_DOWN_DOWN(listOf(VolumeKey.UP, VolumeKey.DOWN, VolumeKey.DOWN), "Up, Down, Down"),
    DOWN_UP_UP(listOf(VolumeKey.DOWN, VolumeKey.UP, VolumeKey.UP), "Down, Up, Up"),
    DOWN_UP_DOWN_UP(listOf(VolumeKey.DOWN, VolumeKey.UP, VolumeKey.DOWN, VolumeKey.UP), "Down, Up, Down, Up"),
    ;

    companion object {
        fun fromId(id: String?): ComboPreset = entries.firstOrNull { it.name == id } ?: UP_DOWN_UP
    }
}
