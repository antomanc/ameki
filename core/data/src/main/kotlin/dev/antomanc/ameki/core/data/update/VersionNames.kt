package dev.antomanc.ameki.core.data.update

/**
 * GitHub release tags are `v0.2.1`; installed [versionName] is `0.2.1`. Compare the numeric
 * dot-separated parts after stripping a leading `v`.
 */
object VersionNames {
    fun normalize(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    /** Positive when [remote] is newer than [installed]. */
    fun compare(remote: String, installed: String): Int {
        val a = parts(normalize(remote))
        val b = parts(normalize(installed))
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(remote: String, installed: String): Boolean = compare(remote, installed) > 0

    private fun parts(version: String): List<Int> =
        version.split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
}
