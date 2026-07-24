package com.ismartcoding.plain.web.security

/**
 * File-path sandbox (spec §5). Lexical normalization here (resolve `.`/`..`, collapse separators) is
 * the platform-agnostic half; the Android layer additionally canonicalizes against the real
 * filesystem (symlink resolution) before calling [isAllowed]. Any path under a denied system root is
 * rejected.
 */
object PathSandbox {
    /** Absolute roots that browser-reachable file ops may never touch. */
    val DENIED_ROOTS: List<String> = listOf(
        "/data", "/proc", "/sys", "/system", "/apex", "/vendor", "/dev", "/root",
    )

    /**
     * Lexically normalize an absolute POSIX path: collapse `//`, drop `.`, and resolve `..` without
     * escaping the root. Relative input is treated as rooted at `/`. Returns a canonical `/...` form.
     */
    fun normalize(path: String): String {
        val out = ArrayDeque<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> {} // skip empty (from // or leading /) and current-dir
                ".." -> if (out.isNotEmpty()) out.removeLast() // cannot escape root
                else -> out.addLast(segment)
            }
        }
        return "/" + out.joinToString("/")
    }

    /**
     * True if [path] (after [normalize]) is not the same as, or nested under, any denied root.
     * The caller should pass an already-OS-canonicalized path on Android.
     */
    fun isAllowed(path: String): Boolean {
        val norm = normalize(path)
        for (root in DENIED_ROOTS) {
            if (norm == root || norm.startsWith("$root/")) return false
        }
        return true
    }

    /** Convenience: normalize + allow check, for callers that only have a lexical path. */
    fun isAllowedLexical(path: String): Boolean = isAllowed(path)
}
