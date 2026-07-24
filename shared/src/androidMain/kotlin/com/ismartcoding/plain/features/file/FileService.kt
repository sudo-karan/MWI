package com.ismartcoding.plain.features.file

import android.os.Environment
import android.os.StatFs
import com.ismartcoding.plain.platform.AndroidApp
import com.ismartcoding.plain.web.security.PathSandbox
import java.io.File

/**
 * Sandboxed filesystem access for the Files domain (spec §6). Every path is **OS-canonicalized**
 * (resolving `..` and symlinks) and then checked against [PathSandbox]'s denied system roots before
 * any access; a denied path throws and the pipeline turns it into a generic error.
 */
object FileService {

    fun mounts(): List<Mount> {
        val result = mutableListOf<Mount>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null) {
            result += primary.toMount("Internal storage", removable = false)
        }
        // Additional (possibly removable) volumes surfaced via app-scoped external dirs.
        AndroidApp.context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile } // strip Android/data/<pkg>/files
            .distinctBy { it.absolutePath }
            .filter { it.absolutePath != primary?.absolutePath && it.exists() }
            .forEach { result += it.toMount(it.name.ifEmpty { "Storage" }, removable = true) }
        return result
    }

    fun files(path: String): List<DFile> {
        val dir = File(sandboxed(path))
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.map { it.toDFile() }?.sortedWith(
            compareByDescending<DFile> { it.isDir }.thenBy { it.name.lowercase() },
        ) ?: emptyList()
    }

    fun fileInfo(path: String): DFile? {
        val f = File(sandboxed(path))
        return if (f.exists()) f.toDFile() else null
    }

    // ------------------------------------------------------------------ write ops

    /** Delete files/dirs (recursively). Returns the number of top-level paths removed. */
    fun deleteFiles(paths: List<String>): Int =
        paths.count { File(sandboxed(it)).deleteRecursively() }

    /** Create a directory (and parents). Returns the created path. */
    fun createDir(path: String): String {
        val dir = File(sandboxed(path))
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("mkdir failed")
        return dir.absolutePath
    }

    /** Rename a file/dir in place. [newName] is a bare name (separators are rejected). */
    fun renameFile(path: String, newName: String): String {
        require(!newName.contains('/') && !newName.contains('\\') && newName.isNotBlank()) { "invalid name" }
        val src = File(sandboxed(path))
        val target = File(sandboxed(File(src.parentFile, newName).path))
        if (!src.renameTo(target)) throw IllegalStateException("rename failed")
        return target.absolutePath
    }

    fun copyFile(src: String, dst: String): String {
        val from = File(sandboxed(src))
        val to = File(sandboxed(dst))
        from.copyRecursively(to, overwrite = false)
        return to.absolutePath
    }

    fun moveFile(src: String, dst: String): String {
        val from = File(sandboxed(src))
        val to = File(sandboxed(dst))
        if (!from.renameTo(to)) {
            from.copyRecursively(to, overwrite = false)
            from.deleteRecursively()
        }
        return to.absolutePath
    }

    /** Write text to a file atomically (temp sibling + rename). */
    fun writeTextFile(path: String, content: String): String {
        val target = File(sandboxed(path))
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp-${content.length}")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return target.absolutePath
    }

    // ------------------------------------------------------ sandboxed resolvers (routes)

    /** A sandboxed [File] that must already exist (for `/fs` reads / `/zip`); null if missing. */
    fun existingFile(path: String): File? {
        val f = File(sandboxed(path))
        return if (f.exists()) f else null
    }

    /** A sandboxed [File] for writing (parent created); the file itself need not exist yet. */
    fun writableFile(path: String): File {
        val f = File(sandboxed(path))
        f.parentFile?.mkdirs()
        return f
    }

    /** Canonicalize then enforce the sandbox; returns the safe canonical path or throws. */
    private fun sandboxed(path: String): String {
        val canonical = File(path).canonicalPath
        if (!PathSandbox.isAllowed(canonical)) throw SecurityException("path denied")
        return canonical
    }

    private fun File.toDFile(): DFile = DFile(
        name = name,
        path = absolutePath,
        isDir = isDirectory,
        size = if (isDirectory) 0 else length(),
        updatedAt = lastModified(),
        childCount = if (isDirectory) (list()?.size ?: 0) else 0,
    )

    private fun File.toMount(name: String, removable: Boolean): Mount {
        val stat = runCatching { StatFs(absolutePath) }.getOrNull()
        return Mount(
            name = name,
            path = absolutePath,
            totalBytes = stat?.totalBytes ?: 0,
            availableBytes = stat?.availableBytes ?: 0,
            removable = removable,
        )
    }
}
