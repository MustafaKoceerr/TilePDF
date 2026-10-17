package com.mustafakoceerr.tilepdf

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Copies the license texts shipped inside a pdfium-binaries archive into
 * `licenses/pdfium/` of a generated assets directory, so the app shows the
 * licenses of exactly the PDFium build it bundles. The archive's own LICENSE
 * (pdfium-binaries, MIT) becomes `pdfium-binaries.txt`.
 */
abstract class CopyPdfiumLicensesTask : DefaultTask() {

    /** One extracted archive; every ABI's carries the same licenses. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun copy() {
        val archive = archiveDir.get()
        fileSystem.sync {
            into(outputDir.dir("licenses/pdfium"))
            from(archive.dir("licenses"))
            from(archive.file("LICENSE")) { rename { "pdfium-binaries.txt" } }
        }
    }
}
