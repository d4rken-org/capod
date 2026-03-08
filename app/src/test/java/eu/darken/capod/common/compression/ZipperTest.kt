package eu.darken.capod.common.compression

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testhelpers.BaseTest
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

class ZipperTest : BaseTest() {

    @TempDir
    lateinit var tempDir: File

    private fun createFile(name: String, content: String): File {
        return File(tempDir, name).also { it.writeText(content) }
    }

    @Nested
    inner class ZipCreation {
        @Test
        fun `zip creates valid zip file`() {
            val file1 = createFile("a.txt", "hello")
            val file2 = createFile("b.txt", "world")
            val zipPath = File(tempDir, "out.zip").path

            Zipper().zip(arrayOf(file1.path, file2.path), zipPath)

            val zipFile = File(zipPath)
            zipFile.exists() shouldBe true
            zipFile.length() shouldBeGreaterThan 0L
        }

        @Test
        fun `zip contains all input files`() {
            val file1 = createFile("core.log", "log content")
            val file2 = createFile("extra.log", "extra content")
            val zipPath = File(tempDir, "out.zip").path

            Zipper().zip(arrayOf(file1.path, file2.path), zipPath)

            ZipFile(zipPath).use { zf ->
                zf.entries().toList().map { it.name } shouldContainExactlyInAnyOrder listOf("core.log", "extra.log")
            }
        }

        @Test
        fun `zip file contents match originals`() {
            val content1 = "first file content"
            val content2 = "second file content"
            val file1 = createFile("a.txt", content1)
            val file2 = createFile("b.txt", content2)
            val zipPath = File(tempDir, "out.zip").path

            Zipper().zip(arrayOf(file1.path, file2.path), zipPath)

            ZipFile(zipPath).use { zf ->
                zf.getInputStream(zf.getEntry("a.txt")).bufferedReader().readText() shouldBe content1
                zf.getInputStream(zf.getEntry("b.txt")).bufferedReader().readText() shouldBe content2
            }
        }

        @Test
        fun `zip with single file works`() {
            val file = createFile("only.txt", "solo")
            val zipPath = File(tempDir, "out.zip").path

            Zipper().zip(arrayOf(file.path), zipPath)

            ZipFile(zipPath).use { zf ->
                zf.entries().toList().map { it.name } shouldBe listOf("only.txt")
            }
        }

        @Test
        fun `zip with empty file includes entry`() {
            val file = createFile("empty.txt", "")
            val zipPath = File(tempDir, "out.zip").path

            Zipper().zip(arrayOf(file.path), zipPath)

            ZipFile(zipPath).use { zf ->
                val entry = zf.getEntry("empty.txt")
                entry.size shouldBe 0L
            }
        }

        @Test
        fun `zip throws on nonexistent input file`() {
            val missing = File(tempDir, "missing.txt").path
            val zipPath = File(tempDir, "out.zip").path

            shouldThrow<FileNotFoundException> {
                Zipper().zip(arrayOf(missing), zipPath)
            }
        }
    }
}
