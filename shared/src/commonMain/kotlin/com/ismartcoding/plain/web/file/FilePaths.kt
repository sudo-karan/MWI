package com.ismartcoding.plain.web.file

import com.ismartcoding.plain.web.security.PathSandbox

/** Pure path helpers for the file-serving routes (zip entry naming, download filenames). */
object FilePaths {

    /**
     * The forward-slash relative name of [childPath] inside a zip rooted at [rootPath]. If the child
     * is not under the root (shouldn't happen post-sandbox), falls back to its basename.
     */
    fun zipEntryName(rootPath: String, childPath: String): String {
        val root = PathSandbox.normalize(rootPath)
        val child = PathSandbox.normalize(childPath)
        val rootPrefix = if (root == "/") "/" else "$root/"
        return if (child.startsWith(rootPrefix)) {
            child.removePrefix(rootPrefix)
        } else {
            child.substringAfterLast('/')
        }
    }

    /**
     * A safe filename for a `Content-Disposition: attachment` header — strips path separators and
     * control characters so a crafted name can't traverse or inject header structure.
     */
    fun sanitizeDownloadName(name: String): String {
        val cleaned = buildString(name.length) {
            for (c in name) {
                when {
                    c == '/' || c == '\\' -> append('_')
                    c == '"' -> append('_')
                    c.code < 0x20 -> append('_') // control chars, incl. CR/LF
                    else -> append(c)
                }
            }
        }.trim().trim('.')
        return cleaned.ifEmpty { "download" }
    }
}
