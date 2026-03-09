package eu.darken.capod.common.compression

import eu.darken.capod.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.capod.common.debug.logging.log
import eu.darken.capod.common.debug.logging.logTag
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Zipper {

    @Throws(Exception::class)
    fun zip(files: List<String>, zipFile: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { out ->
            for (file in files) {
                log(TAG, VERBOSE) { "Compressing $file into $zipFile" }
                val origin = BufferedInputStream(FileInputStream(file), BUFFER)

                val entry = ZipEntry(file.substring(file.lastIndexOf("/") + 1))
                out.putNextEntry(entry)

                origin.use { input -> input.copyTo(out) }
            }
            out.finish()
        }
    }

    companion object {
        internal val TAG = logTag("Zipper")
        const val BUFFER = 2048
    }
}
