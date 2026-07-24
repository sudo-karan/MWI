package com.ismartcoding.plain.web.file

import kotlin.test.Test
import kotlin.test.assertEquals

class FilePathsTest {

    @Test
    fun zipEntryName_isRelativeToRoot() {
        assertEquals("a/b.txt", FilePaths.zipEntryName("/sdcard/x", "/sdcard/x/a/b.txt"))
        assertEquals("b.txt", FilePaths.zipEntryName("/sdcard/x", "/sdcard/x/b.txt"))
        assertEquals("file.txt", FilePaths.zipEntryName("/root/dir", "/other/file.txt")) // not under root
    }

    @Test
    fun sanitizeDownloadName_stripsUnsafeChars() {
        assertEquals("etc_passwd", FilePaths.sanitizeDownloadName("etc/passwd"))
        assertEquals("a_b.txt", FilePaths.sanitizeDownloadName("a\\b.txt"))
        assertEquals("download", FilePaths.sanitizeDownloadName("   "))
        assertEquals("no_quote", FilePaths.sanitizeDownloadName("no\"quote"))
        // CR/LF header-injection attempt collapses to underscores.
        assertEquals("evil__x", FilePaths.sanitizeDownloadName("evil\r\nx"))
    }
}
