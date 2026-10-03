package cn.tgbug.nekotodo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFilesTest {

    @Test
    fun `按扩展名判断支持与否`() {
        assertTrue(SupportedFiles.isSupported("作业清单.docx", null))
        assertTrue(SupportedFiles.isSupported("photo.JPG", null))
        assertTrue(SupportedFiles.isSupported("readme.md", null))
        assertTrue(SupportedFiles.isSupported("data.csv", "text/csv"))
    }

    @Test
    fun `不支持的扩展名要被挡住`() {
        assertFalse(SupportedFiles.isSupported("archive.zip", "application/zip"))
        assertFalse(SupportedFiles.isSupported("program.exe", null))
        assertFalse(SupportedFiles.isSupported("clip.mov", "video/quicktime"))
    }

    @Test
    fun `没有文件名时退回看 MIME`() {
        assertTrue(SupportedFiles.isSupported(null, "image/jpeg"))
        assertTrue(SupportedFiles.isSupported(null, "application/pdf"))
        assertTrue(SupportedFiles.isSupported("noextension", "text/plain"))
        assertFalse(SupportedFiles.isSupported(null, "video/mp4"))
        assertFalse(SupportedFiles.isSupported(null, null))
    }

    @Test
    fun `扩展名大小写不敏感`() {
        assertEquals("png", SupportedFiles.extensionOf("Photo.PNG"))
        assertTrue(SupportedFiles.isSupported("A.PdF", null))
    }

    @Test
    fun `扩展名后面的内容不会被误判`() {
        assertEquals("gz", SupportedFiles.extensionOf("backup.tar.gz"))
        assertFalse(SupportedFiles.isSupported("backup.tar.gz", null))
    }
}
