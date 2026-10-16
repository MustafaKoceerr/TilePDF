package com.mustafakoceerr.tilepdf

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Downloads one prebuilt PDFium archive, refuses it unless its SHA-256 matches
 * the pinned value, and extracts it. Up to date while url, hash and output stay
 * the same, so builds after the first one don't touch the network.
 */
@DisableCachingByDefault(because = "Downloading is not worth caching")
abstract class DownloadPdfiumTask : DefaultTask() {

    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val sha256: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun download() {
        val archive = File(temporaryDir, "pdfium.tgz")
        URI(url.get()).toURL().openStream().use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        val actual = sha256Of(archive)
        if (actual != sha256.get()) {
            archive.delete()
            throw GradleException(
                "PDFium archive ${url.get()} has SHA-256 $actual, expected ${sha256.get()}. Refusing to use it.",
            )
        }
        fileSystem.sync {
            from(archives.tarTree(archives.gzip(archive)))
            into(outputDir)
        }
        archive.delete()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
